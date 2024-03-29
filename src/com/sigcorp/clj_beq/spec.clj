(ns com.sigcorp.clj-beq.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.java.jdbc.spec :as jdbc]))

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
(s/def ::system-code (s/and string? not-empty))
(s/def ::event-code (s/and string? not-empty))
(s/def ::status-ind (s/with-gen (s/and string? #(= 1 (count %)))
                                #(s/gen #{"0" "1" "2" "9"})))
(s/def ::user-id (s/and string? #(<= (count %) 30)))
(s/def ::activity-date inst?)
(s/def ::surrogate-id number?)
(s/def ::version number?)
(s/def ::data-origin string?)
(s/def ::vpdi-code string?)
(s/def ::data (s/map-of string? string?))

(s/def ::event (s/keys :req-un [::seqno ::system-code ::event-code ::status-ind ::activity-date ::db]
                       :opt-un [::user-id ::surrogate-id ::version ::data-origin ::vpdi-code ::data]))

;;
;; events functions
;;
(s/def ::max-rows int?)
(s/def ::get-data boolean?)

(s/fdef com.sigcorp.clj-beq.events/get-events
        :args (s/cat :db ::db
                     :opts (s/keys :opt-un [::max-rows ::get-data ::user-id])
                     :system-code ::system-code
                     :event-code (s/nilable ::event-code)
                     :status (s/nilable ::status-ind))
        :ret (s/* ::event))

(s/fdef com.sigcorp.clj-beq.events/update-event-status!
        :args (s/cat :db ::db
                     :user-id ::user-id
                     :status-ind ::status-ind
                     :wheres (s/+ (s/cat :column keyword? :value any?))))

;;
;; process functions
;;
(s/def ::error-status ::status-ind)
(s/def ::throwable (s/with-gen #(instance? Throwable %)
                               #(s/gen #{(ex-info "Generated throwable" {})})))
(s/def ::event-error-handler (s/fspec :args (s/cat :event ::event :error ::throwable)))
(s/def ::on-event-error (s/nilable ::event-error-handler))

(s/def ::claim-fn (s/fspec :args (s/cat)
                           :ret (s/with-gen (s/* ::event)
                                            #(s/gen (s/coll-of ::event :max-count 5)))))

(s/def ::event-handler (s/fspec :args (s/cat :event ::event)
                                :ret ::status-ind))

(s/def ::finalizer-args (s/cat :event ::event :status ::status-ind))
(s/def ::finalizer (s/fspec :args ::finalizer-args))

(s/fdef com.sigcorp.clj-beq.process/process-events
        :args (s/cat :opts (s/keys :opt-un [::error-status ::on-event-error])
                     :claim-fn ::claim-fn
                     :handler-fn ::event-handler
                     :finalize-fn ::finalizer)
        :ret number?)

(s/fdef com.sigcorp.clj-beq.process/db-update-finalizer
        :args (s/cat :db ::db :final-user ::user-id)
        :ret ::finalizer)

(s/fdef com.sigcorp.clj-beq.process/event-dispatcher
        :args (s/cat :handlers (s/+ ::event-handler))
        :ret ::event-handler)

(s/fdef com.sigcorp.clj-beq.process/handler-for
        :args (s/cat :system-code ::system-code :event-code ::event-code :f ::event-handler)
        :ret ::event-handler)

;;
;; runner configuration
;;
(s/def ::poll-interval number?)
(s/def ::jdbc-url string?)
(s/def ::jdbc-user string?)
(s/def ::jdbc-password string?)

(s/def ::enable-default-handler boolean?)

(s/def ::type string?)
(s/def ::key string?)
(s/def ::step-status #{:success :failure})

(s/def ::handler-step (s/keys :req-un [::type]
                              :opt-un [::key]))

(s/def ::steps (s/* ::handler-step))
(s/def ::step-return (s/keys :req-un [::step-status]))

(s/def ::event-handler-spec (s/keys :req-un [::event-code ::steps]))

(s/def ::event-handlers (s/* ::event-handler-spec))

;; shell steps
(s/def ::command string?)
(s/def ::chdir string?)
(s/def ::shell-cmd (s/+ string?))
(s/def ::success-exit-code int?)
(s/def ::shell-opts (s/keys :req-un [::command]
                            :opt-un [::chdir ::shell-cmd ::success-exit-code]))

;; twilio steps
(s/def ::twilio-acct-sid string?)
(s/def ::twilio-username string?)
(s/def ::twilio-password string?)
(s/def ::twilio-from-number string?)
(s/def ::to-number-parm string?)
(s/def ::body-parm string?)
(s/def ::twilio-opts (s/keys :req-un [::twilio-username ::twilio-password ::twilio-from-number]
                             :opt-un [::twilio-acct-sid ::to-number-parm ::body-parm]))

;; dbms-pipe-send steps
(s/def ::pipe-name string?)
(s/def ::timeout number?)
(s/def ::max-pipe-size number?)
(s/def ::message-items (s/* string?))
(s/def ::dbms-pipe-send-opts (s/keys :req-un [::pipe-name]
                                     :opt-un [::timeout ::max-pipe-size ::message-items]))

;; http steps
(s/def ::method string?)
(s/def ::url string?)
(s/def ::request (s/keys))
(s/def ::http-opts (s/keys :req-un [::method ::url]
                           :opt-un [::request]))

(s/def ::runner-opts (s/keys
                       :req-un [::jdbc-url ::system-code]
                       :opt-un [::jdbc-url ::jdbc-user ::jdbc-password ::event-handlers ::enable-default-handler
                                ::db ::claim-fn ::finalizer]))