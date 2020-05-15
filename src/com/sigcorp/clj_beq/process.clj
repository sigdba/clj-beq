(ns com.sigcorp.clj-beq.process
  (:require [com.sigcorp.clj_beq.events :as e]
            [clojure.tools.logging :as log]))

(defn- pretend-handler [event]
  (log/infof "Handling an event: %s" event)
  "2")

(let [db com.sigcorp.clj_beq.db/DB
      in-opts {}
      system-code "CLJ"
      event-code nil
      handler-fn pretend-handler
      finalizer (fn [db user-id event status]
                  (e/update-event-status! db user-id status :seqno (:seqno event)))]
  (let [{:keys [final-user] :or {final-user "clj-beq"} :as opts} in-opts
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
