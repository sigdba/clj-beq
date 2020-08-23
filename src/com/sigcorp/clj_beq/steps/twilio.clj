(ns com.sigcorp.clj-beq.runners.twilio
  (:import [com.twilio.rest.api.v2010.account Message]
           [com.twilio.type PhoneNumber]
           (com.twilio.http TwilioRestClient$Builder))
  (:use [com.sigcorp.clj-beq.util])
  (:require [com.sigcorp.clj-beq.spec :as ss]
            [com.sigcorp.clj-beq.events :as e]
            [taoensso.timbre :as log]))

(defn- -client-with [twilio-username twilio-password twilio-acct-sid]
  (let [builder (new TwilioRestClient$Builder twilio-username twilio-password)]
    (when twilio-acct-sid
      (.accountSid builder twilio-acct-sid))
    (.build builder)))

(def client-with (memoize -client-with))

(defn- client-with-opts [opts]
  (let [{:keys [twilio-username twilio-password twilio-acct-sid]} opts]
    (client-with twilio-username twilio-password twilio-acct-sid)))

(defn send-sms!
  [opts to body]
  (let [{:keys [twilio-from-number]} opts]
    (log/debugf "Sending message from %s to %s via Twilio" twilio-from-number to)
    (.. Message
        (creator (new PhoneNumber to)
                 (new PhoneNumber twilio-from-number)
                 ^String body)
        (create (client-with-opts opts))
        getSid)))

(defn twilio-step-fn
  [spec]
  (let [opts (conform-or-throw ::ss/twilio-opts "Invalid or missing Twilio settings" spec)
        {:keys [to-number-parm body-parm]
         :or   {to-number-parm "PHONE"
                body-parm      "MESSAGE"}} opts]
    (fn [event]
      (let [to (e/require-parm event to-number-parm)
            body (e/require-parm event body-parm)]
        (send-sms! spec to body)
        {:step-status :success}))))
