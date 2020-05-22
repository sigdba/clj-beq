(ns com.sigcorp.clj-beq.runner
  (:require [com.sigcorp.clj-beq.process :as p]
            [com.sigcorp.clj_beq.db :as db]
            [com.sigcorp.clj-beq.spec :as ss]
            [com.sigcorp.clj_beq.events :as e]
            [com.sigcorp.clj-beq.runners.twilio :as twilio]
            [com.sigcorp.clj-beq.runners.shell :as shell]
            [clj-yaml.core :as yaml]
            [taoensso.timbre :as log])
  (:use [com.sigcorp.clj-beq.util]))

(defn load-conf [path]
  (log/debugf "Loading config file: %s" path)
  (->> path slurp yaml/parse-string
       (conform-or-throw ::ss/conf "Error parsing configuration")))

(defn- handler-with-spec [conf spec]
  (let [{:keys [system-code]} conf
        globals (dissoc conf :event-handlers)
        spec-with-globals (merge globals spec)
        _ (log/debugf "spec-with-globals: %s" spec-with-globals)
        opts (conform-or-throw ::ss/event-handler-spec "Invalid event handler spec" spec-with-globals)
        {:keys [type event-code]} opts
        factory (case type
                  "shell" shell/shell-handler
                  "twilio" twilio/twilio-event-handler
                  (throw (ex-info (str "unrecognized handler type: " type) {:spec spec})))]
    (p/handler-for system-code event-code (factory spec-with-globals))))

(defn runner-with-conf-path [path]
  (let [{:keys [system-code event-handlers jdbc-url jdbc-user jdbc-password] :as conf} (load-conf path)
        handler (->> event-handlers
                     (map #(handler-with-spec conf %))
                     vec                                    ; Convert to a vector to realize all handlers immediately
                     p/event-dispatcher)
        db (db/db-with jdbc-url jdbc-user jdbc-password)

        ;; jdbc-user is optional, so ask the DB what our actual username is
        db-user (->> (db/query db ["select user from dual"]) first :user)]

    (fn []
      (p/process-events conf
                        #(e/claim-events! db conf system-code nil)
                        handler
                        (p/db-update-finalizer db db-user)))))
