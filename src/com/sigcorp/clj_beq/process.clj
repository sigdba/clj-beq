(ns com.sigcorp.clj-beq.process
  (:require [com.sigcorp.clj_beq.events :as e]
            [clojure.tools.logging :as log]))

(defn process-events!
  "claim, handle, and finalize a single batch of events"
  [db opts system-code event-code handler-fn finalizer]
  (let [{:keys [final-user] :or {final-user "clj-beq"}} opts
        events (e/claim-events! db opts system-code event-code)]
    (log/debugf "Claimed %d events" (count events))
    (loop [remaining-events events]
      (let [event (first remaining-events)]
        (when-let [seqno (if event (biginteger (:seqno event)))]
          (log/debugf "Processing event: %s" event)
          (let [res (handler-fn event)]
            (log/debugf "Handler (%s) returned '%s' for event #%d" handler-fn res seqno)
            (log/debugf "Finalizing event #%d with %s" seqno finalizer)
            (finalizer db final-user event res))
          (recur (rest remaining-events)))))))

(def CLAIMED-EVENTS [{:user-id "e3a148ad7b96842860200dd25acd48",
                      :activity-date #inst"2020-05-15T19:18:24.000000000-00:00",
                      :seqno 1718327M,
                      :eqnm-code "SOME_EVENT",
                      :status-ind "1",
                      :eqts-code "CLJ",
                      :data {}}])

(let [claim-fn (constantly CLAIMED-EVENTS)
      handler-fn (fn [event] (log/infof "Handling an event: %s" event) "2")
      finalize-fn (fn [event status] (log/debugf "Finalizing event %s, status: %s" (:seqno event) status))]
  (->> (claim-fn)
       (map (fn [event] [event (handler-fn event)]))
       (map #(apply finalize-fn %))
       doall))

#_(process-events! com.sigcorp.clj_beq.db/DB
                 {}
                 "CLJ"
                 nil
                 (fn [event] (log/infof "Handling an event: %s" event) "2")
                 (fn [db user-id event status] (e/update-event-status! db user-id status :seqno (:seqno event))))
