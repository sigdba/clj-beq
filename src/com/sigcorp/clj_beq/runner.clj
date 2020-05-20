(ns com.sigcorp.clj-beq.runner
  (:require [com.sigcorp.clj-beq.process :as p]
            [com.sigcorp.clj_beq.db :as db]
            [com.sigcorp.clj-beq.spec :as ss]
            [clj-yaml.core :as yaml]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [com.sigcorp.clj_beq.events :as e])
  (:use [com.sigcorp.clj-beq.util]
        [clojure.java.shell]))

(defn load-conf [path]
  (log/debugf "Loading config file: %s" path)
  (->> path slurp yaml/parse-string
       (conform-or-throw ::ss/conf "Error parsing configuration")))

(defn- shell-success? [spec res]
  (let [{:keys [success-exit-code]
         :or   {success-exit-code 0}} spec
        {:keys [exit]} res]
    (= success-exit-code exit)))

(defn- prefix-lines [prefix s]
  (str/replace s #"(?m)^" prefix))

(defn- out-block [label s]
  (if (< 0 (-> s str/trim count))
    (format "\n /-- %s --\n%s" label (prefix-lines " | " s))
    ""))

(defn- log-shell-res [event res]
  (let [{:keys [seqno]} event
        {:keys [exit out err]} res]
    (log/debugf "Event %s, exit code: %d%s%s"
                seqno exit (out-block "STDOUT" out) (out-block "STDERR" err))))

(defn shell-handler
  "returns an event handler function for the given event-handler spec"
  [system-code spec]
  (let [{:keys [event-code command env chdir shell-cmd success-status-ind fail-status-ind]
         :or   {chdir              (System/getProperty "user.dir")
                shell-cmd          ["/bin/bash" "-c"]
                success-status-ind "2"
                fail-status-ind    "9"}} spec
        success-fn (partial shell-success? spec)
        log-res-fn (partial log-shell-res)]

    (p/handler-for
      system-code event-code
      (fn [{:keys [data seqno] :as event}]
        (let [full-env (merge (into {} (System/getenv)) env data)
              pos-args (conj shell-cmd command)
              sh-args (into pos-args [:env full-env :dir chdir])]
          (log/debugf "Event %s: Executing: %s" seqno pos-args)
          (let [res (apply sh sh-args)]
            (log-res-fn event res)
            (if (success-fn res) success-status-ind fail-status-ind)))))))

(defn runner-with-conf-path [path]
  (let [{:keys [system-code event-handlers jdbc-url jdbc-user jdbc-password] :as conf} (load-conf path)
        handler (->> event-handlers
                     (map #(shell-handler system-code %))
                     p/event-dispatcher)
        db (db/db-with jdbc-url jdbc-user jdbc-password)

        ;; jdbc-user is optional, so ask the DB what our actual username is
        db-user (->> (db/query db ["select user from dual"]) first :user)]

    (fn []
      (p/process-events conf
                        #(e/claim-events! db conf system-code nil)
                        handler
                        (p/db-update-finalizer db db-user)))))
