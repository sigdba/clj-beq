(ns com.sigcorp.clj-beq.runners.twilio
  (:import [com.twilio Twilio]
           [com.twilio.rest.api.v2010.account Message]
           [com.twilio.type PhoneNumber])
  (:use [com.sigcorp.clj-beq.util])
  (:require [com.sigcorp.clj-beq.spec :as ss]
            [com.sigcorp.clj_beq.events :as e]
            [clojure.tools.logging :as log]))

***REMOVED***
***REMOVED***
***REMOVED***
***REMOVED***

(defn send-sms!
  [opts to body]
  (let [{:keys [twilio-acct-sid twilio-auth-token twilio-from-number]} opts]
    (log/debugf "Sending message to %s via Twilio" to)
    (Twilio/init twilio-acct-sid twilio-auth-token)
    (.. Message (creator (new PhoneNumber to)
                         (new PhoneNumber twilio-from-number)
                         ^String body)
        create
        getSid)))

(defn twilio-event-handler
  [spec]
  (let [opts (conform-or-throw ::ss/twilio-opts "Invalid or missing Twilio settings" spec)
        {:keys [to-number-parm body-parm]
         :or   {to-number-parm "TO"
                body-parm      "BODY"}} opts]
    (fn [event]
      (let [to (e/require-parm event to-number-parm)
            body (e/require-parm event body-parm)]
        (send-sms! spec to body)))))

***REMOVED***
***REMOVED***
***REMOVED***
***REMOVED***
***REMOVED***
***REMOVED***
***REMOVED***
***REMOVED***
***REMOVED***

***REMOVED***
  ***REMOVED***
  ***REMOVED***
***REMOVED***