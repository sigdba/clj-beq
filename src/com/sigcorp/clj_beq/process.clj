(ns com.sigcorp.clj-beq.process
  (:require [com.sigcorp.clj_beq.events :as e]
            [clojure.tools.logging :as log]))

(defn process-events [claim-fn handler-fn finalize-fn]
  (->> (claim-fn)
       (map (fn [event] [event (handler-fn event)]))
       (map #(apply finalize-fn %))
       doall))

(defn db-update-finalizer [db final-user]
  (fn [event status]
    (e/update-event-status! db final-user status :seqno (:seqno event))))

(def CLAIMED-EVENTS [{:user-id "e3a148ad7b96842860200dd25acd48",
                      :activity-date #inst"2020-05-15T19:18:24.000000000-00:00",
                      :seqno 1718327M,
                      :eqnm-code "SOME_EVENT",
                      :status-ind "1",
                      :eqts-code "CLJ",
                      :data {}}])

#_(process-events (constantly CLAIMED-EVENTS)
                (fn [event] (log/infof "Handling an event: %s" event) "2")
                (fn [event status] (log/infof "Finalizing event %s with status %s" (:seqno event) status)))

#_(process-events #(e/claim-events! com.sigcorp.clj_beq.db/DB {} "CLJ" nil)
                (fn [event] (log/infof "Handling an event: %s" event) "2")
                (db-update-finalizer com.sigcorp.clj_beq.db/DB "beq-clj"))
