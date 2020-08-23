(ns com.sigcorp.clj-beq.runner
  "Functions for the `runner` sub-command which provides event handling using 'runners'."
  (:require [com.sigcorp.clj-beq.process :as p]
            [com.sigcorp.clj-beq.db :as db]
            [com.sigcorp.clj-beq.spec :as ss]
            [com.sigcorp.clj-beq.events :as e]
            [com.sigcorp.clj-beq.runners.twilio :as twilio]
            [com.sigcorp.clj-beq.runners.shell :as shell]
            [taoensso.timbre :as log])
  (:use [com.sigcorp.clj-beq.util]))

(defn- conform-handler-spec [defaults spec]
  (conform-or-throw ::ss/event-handler-spec "Invalid event handler spec"
                    (merge defaults spec)))

;; This function is placeholder for a future where we have dynamic loading of step functions. Do not add any other
;; logic to it beyond discovering the function based on step type.
(defn- step-factory-with-type
  "Returns the 'factory' function for the step spec'd step function."
  [type]
  (case type
    "shell" shell/shell-handler
    "twilio" twilio/twilio-step-fn
    (throw (ex-info (str "unrecognized handler type: " type) {}))))

(defn- step-fn-with-spec
  "Returns a step function for the given `spec`.
  The returned function will accept an event, handle it, and return the event along with any other information returned
  by the handling function."
  [spec]
  (let [{:keys [type key]} spec
        result-key (keyword (or key type))
        sf ((step-factory-with-type type) spec)]
    (fn [event]
      (log/tracef "Performing step function %s" result-key)
      (let [ret (->> (sf event)
                     (conform-or-throw ::ss/step-return
                                       (format "Step function %s returned invalid result" result-key)))]

        ;; Throw an exception if the step was not a :success.
        ;; TODO: We might want to add more interesting error handling here later.
        (when-not (->> ret :step-status (= :success))
          (throw (ex-info (format "Error processing step: %s" result-key) ret)))

        ;; Return the original event along with the step function's results.
        (assoc event (keyword result-key) ret)))))

(defn- handler-with-spec [conf spec]
  (let [{:keys [system-code]} conf
        opts (conform-handler-spec conf spec)
        {:keys [event-code steps]} opts
        handler (->> steps
                     (map #(merge opts %))
                     (map step-fn-with-spec)
                     reverse
                     (reduce comp))]
    (p/handler-for system-code event-code (fn [event] (handler event) "2"))))

(defn- handler-specs-with-conf [conf]
  (let [{:keys [enable-default-handler event-handlers]} conf]
    (into (if enable-default-handler [{}] []) event-handlers)))

(defn- handler-with-conf [conf]
  (->> (handler-specs-with-conf conf)                       ; Take the event handlers from the configuration
       (map #(handler-with-spec conf %))                    ; Convert into handler functions
       vec                                                  ; Convert to a vector to realize all handlers immediately
       p/event-dispatcher))                                 ; Return dispatch function for the handlers

(defn- db-with-conf [conf]
  (let [{:keys [db jdbc-url jdbc-user jdbc-password]} conf]
    (if db db
           (db/db-with jdbc-url jdbc-user jdbc-password))))

(defn- claim-fn-with [conf db]
  (let [{:keys [claim-fn system-code]} conf]
    (if claim-fn claim-fn
                 #(e/claim-events! db conf system-code nil))))

(defn- sleep-fn-with [conf db]
  (let [{:keys [event-pipe-name event-pipe-timeout event-alert-name event-alert-timeout poll-interval]
         :or   {event-pipe-timeout  3600
                event-alert-timeout 3600
                poll-interval       30}} conf]

    ;; TODO: Ideally the alert and pipe methods wouldn't be mutually exclusive. We could use some basic async tools
    ;;       to create both methods and return when the first one fires. Note that this would require multiple
    ;;       connections because we can't have a single JDBC connection blocking on two things at once. Or can we?
    (cond
      ;; If an alert was specified in the configuration, return a function which waits for it.
      event-alert-name
      #(do (log/debugf "Waiting for DBMS_ALERT '%s' (timeout %d seconds)" event-alert-name event-alert-timeout)
           (db/wait-on-alert db event-alert-name event-alert-timeout))

      ;; If a pipe was specified in the configuration, return a function which waits for events on it.
      event-pipe-name
      #(do (log/debugf "Waiting for event on pipe '%s' (timeout %d seconds)" event-pipe-name event-pipe-timeout)
           (db/wait-on-pipe! db event-pipe-name event-pipe-timeout true))

      ;; Otherwise, return a function which calls Thread/sleep
      :else
      #(do (log/debugf "Napping %d seconds" poll-interval)
           (Thread/sleep (* poll-interval 1000))))))

(defn- finalizer-with
  "Returns a 'finalizer' function based on options in `conf`.
  A finalizer is a function which accepts an event and a status and updates the queue. If a `:finalizer` is already
  ;set in `conf` then it is returned. Otherwise a new finalizer is returned using
  [[com.sigcorp.clj-beq.process/db-update-finalizer]]"
  [conf db]
  (let [{:keys [finalizer]} conf]
    (if finalizer finalizer
                  (p/db-update-finalizer db))))

(defn runner-with-conf
  "Returns a 'runner' function based on options in `conf`.
  A runner is a 0-arity function which fetches & processes a single block of events then returns their count."
  [conf db]
  (let [handler (handler-with-conf conf)
        claim-fn (claim-fn-with conf db)
        finalizer (finalizer-with conf db)]
    (fn []
      (log/debug "Fetching events...")
      (let [count (p/process-events conf claim-fn handler finalizer)]
        (log/debugf "Processed %d events" count)
        count))))

(defn run
  "Processes events with `runner` function, looping depending on `mode` and napping where appropriate using `sleep-fn`."
  [runner sleep-fn mode]
  (loop []
    (let [count (runner)]
      (cond (= :single mode) nil                            ; If we're in single mode, do one batch and return
            (> count 0) (recur)                             ; If there were events, cycle again without waiting
            (= :continuous mode) (do (sleep-fn) (recur))    ; If there weren't events, sleep before cycling again
            :else nil))))                                   ; If we're in batch mode, return when the queue is empty

(defn run-with-opts
  "Calls the `run` function using options specified in `conf`."
  [conf _]
  (let [{:keys [mode]} conf
        db (db-with-conf conf)]
    (run (runner-with-conf conf db)
         (sleep-fn-with conf db)
         mode)))
