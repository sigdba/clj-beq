(ns com.sigcorp.clj-beq.daemon
  (:require [com.sigcorp.clj-beq.process :as p]
            [clj-yaml.core :as yaml]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:use [com.sigcorp.clj-beq.util]
        [clojure.java.shell]))

(s/def ::system_code string?)
(s/def ::event_code string?)
(s/def ::command string?)
(s/def ::chdir string?)
(s/def ::shell_cmd (s/+ string?))
(s/def ::success_status_ind :com.sigcorp.clj_beq.events/status-ind)
(s/def ::fail_status_ind :com.sigcorp.clj_beq.events/status-ind)
(s/def ::success_exit_code int?)
(s/def ::event-handler (s/keys :req-un [::system_code ::event_code ::command]
                               :opt-un [::chdir ::shell_cmd ::success_status_ind ::fail_status_ind
                                        ::success_exit_code]))
(s/def ::event_handlers (s/* ::event-handler))
(s/def ::conf (s/keys :opt-un [::event_handlers]))

(defn load-conf [path]
  (->> path slurp yaml/parse-string
       (conform-or-throw ::conf "Error parsing configuration")))

(def EVENT {:user-id       "e3a148ad7b96842860200dd25acd48",
            :activity-date #inst"2020-05-15T19:18:24.000000000-00:00",
            :seqno         1718327M,
            :eqnm-code     "SOME_EVENT",
            :status-ind    "1",
            :eqts-code     "CLJ",
            :data          {"PLACE" "/tmp"}})

(defn- shell-success? [spec res]
  (let [{:keys [success_exit_code]
         :or   {success_exit_code 0}} spec
        {:keys [exit]} res]
    (= success_exit_code exit)))

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
  (let [{:keys [system_code event_code command env chdir shell_cmd success_status_ind fail_status_ind]
         :or   {chdir              (System/getProperty "user.dir")
                shell_cmd          ["/bin/bash" "-c"]
                success_status_ind "2"
                fail_status_ind    "9"}} spec
        success-fn (partial shell-success? spec)
        log-res-fn (partial log-shell-res)]

    (p/handler-for
      system_code event_code
      (fn [{:keys [data seqno] :as event}]
        (let [full-env (merge (into {} (System/getenv)) env data)
              pos-args (conj shell_cmd command)
              sh-args (into pos-args [:env full-env :dir chdir])]
          (log/debugf "Event %s: Executing: %s" seqno pos-args)
          (let [res (apply sh sh-args)]
            (log-res-fn event res)
            (if (success-fn res) success_status_ind fail_status_ind)))))))

#_(let [spec (->> "sample-conf.yml" load-conf :event_handlers first)]
  (-> (shell-handler spec)
      (apply [EVENT])))

#_(let [path "sample-conf.yml"
        conf (load-conf path)]
    (let [{:keys [event_handlers]} conf]
      (->> event_handlers)))
