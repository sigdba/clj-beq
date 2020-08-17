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

(defn handler-factory-with-spec [spec]
  (let [type (:type spec)]
    (case type
      "shell" shell/shell-handler
      "twilio" twilio/twilio-event-handler
      (throw (ex-info (str "unrecognized handler type: " type) {:spec spec})))))

(defn- handler-with-spec [conf spec]
  (let [{:keys [system-code]} conf
        opts (conform-handler-spec conf spec)
        {:keys [event-code]} opts
        factory (handler-factory-with-spec opts)]
    (p/handler-for system-code event-code (factory opts))))

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
  (let [{:keys [event-pipe-name event-pipe-timeout poll-interval]
         :or   {event-pipe-timeout 3600 poll-interval 30}} conf]
    (if event-pipe-name
      ;; If a pipe was specified in the configuration, return a function which waits for events on it.
      #(do (log/debugf "Waiting for event on pipe '%s' (timeout %d seconds)" event-pipe-name event-pipe-timeout)
           (db/wait-on-pipe! db event-pipe-name event-pipe-timeout true))

      ;; Otherwise, return a function which calls Thread/sleep
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
  (let [handler (handler-with-conf conf)]
    (fn []
      (log/debug "Fetching events...")
      (let [count (p/process-events conf
                                    (claim-fn-with conf db)
                                    handler
                                    (finalizer-with conf db))]
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
