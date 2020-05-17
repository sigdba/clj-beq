(ns com.sigcorp.clj-beq.process
  (:require [com.sigcorp.clj_beq.events :as e]
            [clojure.tools.logging :as log])
  (:use [com.sigcorp.clj-beq.util]))

(defn- process-event
  "process a single event, performs error handling, return [event (handler-fn event)]"
  [opts handler-fn event]
  (let [{:keys [error-status on-event-error] :or {error-status "9"}} opts]
    (try
      [event (handler-fn event)]
      (catch Exception e
        (log/errorf e "Error processing event %s" (:seqno event))
        (when on-event-error (on-event-error event e))
        [(assoc event :exception e) error-status]))))

(defn process-events [opts claim-fn handler-fn finalize-fn]
  "process a single batch of events"
  (->> (claim-fn)
       (map #(process-event opts handler-fn %))
       (map #(apply finalize-fn %))
       doall))

(defn- Throwable->str [e]
  "returns a string of the exception e truncated to 2000 characters"
  (->> e Throwable->map str (trunc 2000)))

(defn db-update-finalizer [db final-user]
  (fn [{:keys [seqno]} status]
    (log/debugf "Finalizing event %s" seqno)
    (e/update-event-status! db final-user status :seqno seqno)))

(defn dispatch-event [handlers event]
  (loop [[handler & rest] handlers]
    (let [res (handler event)]
      (case res
        :not-handled (if rest (recur rest)
                              (throw (ex-info "No suitable handler found for event" event)))
        res))))

(def CLAIMED-EVENTS [{:user-id       "e3a148ad7b96842860200dd25acd48",
                      :activity-date #inst"2020-05-15T19:18:24.000000000-00:00",
                      :seqno         1718327M,
                      :eqnm-code     "SOME_EVENT",
                      :status-ind    "1",
                      :eqts-code     "CLJ",
                      :data          {}}])

#_(let [event (first CLAIMED-EVENTS)
        handlers [(fn [_] (log/debug "Handler 1 called") :not-handled)
                  (fn [_] (log/debug "Handler 2 called") "2")
                  (fn [_] (log/debug "Handler 3 called") :not-handled)]]
    (dispatch-event handlers event))

#_(process-events {}
                  (constantly CLAIMED-EVENTS)
                  (fn [event] (log/infof "Handling an event: %s" event) "2")
                  (fn [event status] (log/infof "Finalizing event %s with status %s" (:seqno event) status)))

#_(process-events {}
                  #(e/claim-events! com.sigcorp.clj_beq.db/DB {} "CLJ" nil)
                  (fn [event] (log/infof "Handling an event: %s" event) (/ 1 0))
                  (db-update-finalizer com.sigcorp.clj_beq.db/DB "beq-clj"))
