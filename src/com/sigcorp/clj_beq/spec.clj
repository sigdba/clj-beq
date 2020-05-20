(ns com.sigcorp.clj-beq.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.java.jdbc.spec :as jdbc]
            [clojure.spec.gen.alpha :as gen]
            [clojure.tools.logging :as log]))

;;
;; Outside references
;;
(s/def ::db (s/with-gen ::jdbc/db-spec
                        #(s/gen #{{:connection-uri "url"
                                   :user           "user"
                                   :password       "pass"}})))

;;
;; Events
;;
(s/def ::seqno number?)
(s/def ::system-code string?)
(s/def ::event-code string?)
(s/def ::status-ind (s/with-gen (s/and string? #(= 1 (count %)))
                                #(s/gen #{"0" "1" "2" "9"})))
(s/def ::user-id (s/and string? #(<= (count %) 30)))
(s/def ::activity-date inst?)
(s/def ::surrogate-id number?)
(s/def ::version number?)
(s/def ::data-origin string?)
(s/def ::vpdi-code string?)
(s/def ::data (s/map-of string? string?))

(s/def ::event (s/keys :req-un [::seqno ::system-code ::event-code ::status-ind ::activity-date]
                       :opt-un [::user-id ::surrogate-id ::version ::data-origin ::vpdi-code ::data]))

;;
;; Configuration
;;
(s/def ::jdbc-url string?)
(s/def ::jdbc-user string?)
(s/def ::jdbc-password string?)
(s/def ::command string?)
(s/def ::chdir string?)
(s/def ::shell-cmd (s/+ string?))
(s/def ::success-status-ind ::status-ind)
(s/def ::fail-status-ind ::status-ind)
(s/def ::success-exit-code int?)

(s/def ::event-handler (s/keys :req-un [::event-code ::command]
                               :opt-un [::chdir ::shell-cmd ::success-status-ind ::fail-status-ind
                                        ::success-exit-code]))
(s/def ::event-handlers (s/* ::event-handler))
(s/def ::conf (s/keys
                :req-un [::jdbc-url ::system-code]
                :opt-un [::jdbc-url ::jdbc-user ::jdbc-password ::event-handlers]))

;;
;; events functions
;;
(s/def ::max-rows int?)
(s/def ::get-data boolean?)

(s/fdef com.sigcorp.clj_beq.events/get-events
        :args (s/cat :db ::db
                     :opts (s/keys :opt-un [::max-rows ::get-data ::user-id])
                     :system-code ::system-code
                     :event-code (s/nilable ::event-code)
                     :status (s/nilable ::status-ind))
        :ret (s/* ::event))

;;
;; process functions
;;
(s/def ::error-status ::status-ind)
(s/def ::throwable (s/with-gen #(instance? Throwable %)
                               #(gen/fmap (fn [s] (ex-info s {})) (gen/string-alphanumeric))))
(s/def ::event-error-handler (s/fspec :args (s/cat :event ::event :error ::throwable)))
(s/def ::on-event-error (s/nilable ::event-error-handler))

(s/def ::claim-fn (s/fspec :args (s/cat)
                           :ret (s/* ::event)))

(s/def ::event-handler (s/fspec :args (s/cat :event ::event)
                                :ret ::status-ind))

(s/def ::finalizer (s/fspec :args (s/cat :event ::event :status ::status-ind)))

(s/fdef com.sigcorp.clj-beq.process/process-events
        :args (s/cat :opts (s/keys :opt-un [::error-status ::on-event-error])
                     :claim-fn ::claim-fn
                     :handler-fn ::event-handler
                     :finalize-fn ::finalizer))

(s/fdef com.sigcorp.clj-beq.process/db-update-finalizer
        :args (s/cat :db ::db :final-user ::user-id)
        :ret ::finalizer)

(s/fdef com.sigcorp.clj-beq.process/event-dispatcher
        :args (s/cat :handlers (s/+ ::event-handler))
        :ret ::event-handler)

(s/fdef com.sigcorp.clj-beq.process/handler-for
        :args (s/cat :system-code ::system-code :event-code ::event-code :f ::event-handler)
        :ret ::event-handler)

#_(let [spec-ns (namespace ::this)]
    (->> (s/registry)
         (filter (fn [[k _]] (= spec-ns (namespace k))))
         (filter (fn [[_ v]] (s/spec? v)))
         (map first)
         (map (fn [k]
                (log/debugf "exercising %s" k)
                (s/exercise k)))
         doall))
