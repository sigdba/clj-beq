(ns com.sigcorp.clj-beq.runners.shell
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]
            [com.sigcorp.clj-beq.spec :as ss])
  (:use [clojure.java.shell]
        [com.sigcorp.clj-beq.util]))

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
  [spec]

  (let [opts (conform-or-throw ::ss/shell-opts "Invalid or missing shell settings" spec)
        {:keys [command env chdir shell-cmd success-status-ind fail-status-ind]
         :or   {chdir              (System/getProperty "user.dir")
                shell-cmd          ["/bin/bash" "-c"]
                success-status-ind "2"
                fail-status-ind    "9"}} opts
        success-fn (partial shell-success? spec)
        log-res-fn (partial log-shell-res)]

    (fn [{:keys [data seqno] :as event}]
      (let [full-env (merge (into {} (System/getenv)) env data)
            pos-args (conj shell-cmd command)
            sh-args (into pos-args [:env full-env :dir chdir])]
        (log/debugf "Event %s: Executing: %s" seqno pos-args)
        (let [res (apply sh sh-args)]
          (log-res-fn event res)
          (if (success-fn res) success-status-ind fail-status-ind))))))