(ns com.sigcorp.clj-beq.spec
  (:require [clojure.spec.alpha :as s]))

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