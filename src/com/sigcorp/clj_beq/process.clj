(ns com.sigcorp.clj-beq.process
  (:require [com.sigcorp.clj_beq.events :as e]
            [taoensso.timbre :as log])
  (:use [com.sigcorp.clj-beq.util]))

(defn- process-event
  "process a single event, performs error handling, return [event (handler-fn event)]"
  [opts handler-fn event]
  (log/debugf "Processing event: %s" event)
  (let [{:keys [error-status on-event-error] :or {error-status "9"}} opts]
    (try
      [event (handler-fn event)]
      (catch Exception e
        (if (get (ex-data e) :no-stacktrace)
          (log/errorf "Error processing event %s:\n%s" (:seqno event) (ex-message e))
          (log/errorf e "Error processing event %s" (:seqno event)))
        (when on-event-error (on-event-error event e))
        [(assoc event :exception e) error-status]))))

(defn process-events [opts claim-fn handler-fn finalize-fn]
  "process a single batch of events"
  (->> (claim-fn)
       (pmap #(process-event opts handler-fn %))
       (pmap #(apply finalize-fn %))
       doall)
  nil)

(defn- Throwable->str [e]
  "returns a string of the exception e truncated to 2000 characters"
  (->> e Throwable->map str (trunc 2000)))

(defn db-update-finalizer [db final-user]
  (fn [{:keys [seqno]} status]
    (log/debugf "Finalizing event %s" seqno)
    (e/update-event-status! db final-user status :seqno seqno)))

(defn event-dispatcher [handlers]
  (fn [event]
    (loop [[handler & rest] handlers]
      (let [res (handler event)]
        (case res
          :not-handled (if rest (recur rest)
                                (throw (ex-info "No suitable handler found for event" {:event event})))
          res)))))

(defn handler-for [system-code event-code f]
  (fn [{sc :system-code ec :event-code :as event}]
    (if (and (= system-code sc) (= event-code ec))
      (f event)
      :not-handled)))
