(ns com.sigcorp.clj-beq.steps.shell
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]
            [com.sigcorp.clj-beq.templates :refer [expand]]
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

(defn shell-step
  "returns an event handler function for the given event-handler spec"
  [spec]

  (let [opts (conform-or-throw ::ss/shell-opts "Invalid or missing shell settings" spec)
        {:keys [command env chdir shell-cmd]
         :or   {chdir              (System/getProperty "user.dir")
                shell-cmd          ["/bin/bash" "-c"]}} opts
        success-fn (partial shell-success? spec)
        log-res-fn (partial log-shell-res)]

    (fn [{:keys [data seqno] :as event}]
      (let [env-x (expand env event)
            full-env (merge (into {} (System/getenv)) env-x data)
            pos-args (-> shell-cmd (conj command) (expand event))
            sh-args (into pos-args [:env full-env :dir chdir])]
        (log/debugf "Event %s: Executing: %s" seqno pos-args)
        (let [res (apply sh sh-args)]
          (log-res-fn event res)
          (assoc res :step-status (if (success-fn res) :success :failure)))))))