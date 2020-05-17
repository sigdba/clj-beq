(ns com.sigcorp.clj-beq.process
  (:require [com.sigcorp.clj_beq.events :as e]
            [clojure.tools.logging :as log])
  (:use [com.sigcorp.clj-beq.util]))

(defn- process-event
  "process a single event, performs error handling, return [event (handler-fn event)]"
  [opts handler-fn event]
  (let [{:keys [error-status on-event-error] :or {error-status "E"}} opts]
    (try
      [event (handler-fn event)]
      (catch Exception e
        (log/errorf e "Error processing event %s" (:seqno event))
        (when on-event-error (on-event-error event e))
        [(assoc event :exception e) error-status]))))

(defn process-events [opts claim-fn handler-fn finalize-fn]
  (->> (claim-fn)
       (map #(process-event opts handler-fn %))
       (map #(apply finalize-fn %))
       doall))

(defn- Throwable->parm [e]
  "returns a string of the exception e truncated to fit in GOREQRC"
  (->> e Throwable->map str (trunc 2000)))

(defn db-update-finalizer [db opts final-user]
  (let [{:keys [insert-error-parm] :or {insert-error-parm true}} opts
        insert-error-fn (if insert-error-parm
                          (fn [seqno e]
                            (log/debugf "Inserting stacktrace parm for event %s" seqno)
                            (e/update-event-data! db seqno {:exception (Throwable->parm e)}))

                          ;; If insert-error-parm is false, do nothing
                          (fn [& _]))]
    (fn [{:keys [seqno exception]} status]
      (log/debugf "Finalizing event %s" seqno)
      (when exception (insert-error-fn seqno exception))
      (e/update-event-status! db final-user status :seqno seqno))))

#_(def CLAIMED-EVENTS [{:user-id       "e3a148ad7b96842860200dd25acd48",
                      :activity-date #inst"2020-05-15T19:18:24.000000000-00:00",
                      :seqno         1718327M,
                      :eqnm-code     "SOME_EVENT",
                      :status-ind    "1",
                      :eqts-code     "CLJ",
                      :data          {}}])

#_(process-events (constantly CLAIMED-EVENTS)
                  (fn [event] (log/infof "Handling an event: %s" event) "2")
                  (fn [event status] (log/infof "Finalizing event %s with status %s" (:seqno event) status)))

#_(process-events {}
                #(e/claim-events! com.sigcorp.clj_beq.db/DB {} "CLJ" nil)
                (fn [event] (log/infof "Handling an event: %s" event) (/ 1 0))
                (db-update-finalizer com.sigcorp.clj_beq.db/DB {} "beq-clj"))
