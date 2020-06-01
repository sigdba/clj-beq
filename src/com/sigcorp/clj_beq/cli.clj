(ns com.sigcorp.clj-beq.cli
  (:require [com.sigcorp.clj-beq.runner :as r]
            [com.sigcorp.clj-beq.spec :as ss]
            [clojure.tools.cli :as c]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clj-yaml.core :as yaml])
  (:use [com.sigcorp.clj-beq.util])
  (:gen-class))

(defn- add-opt
  "assoc-fn for the -C option; parses this=that parameters and returns the updated map"
  [opts _ o]
  (let [matcher (re-matcher #"^([^=]+)=(.*)$" o)]
    (re-find matcher)
    (->> (re-groups matcher)                                ; extract the match groups
         rest                                               ; drop the first one
         (apply (fn [k v] [(keyword k) v]))                 ; convert the key to a keyword
         (apply assoc opts))))                              ; add it to the option map

(def global-opts [["-c" "--conf CONF" "Configuration file"]
                  [:id :add-opt
                   :short-opt "-C"
                   :required "OPT"
                   :assoc-fn add-opt]
                  ["-h" "--help" "prints this screen"]])

(def COMMANDS {:runner {:desc     "Process events"
                        :args     ""
                        :opts     [["-m" "--mode MODE" "Run mode: batch or continuous"
                                    :default :continuous
                                    :parse-fn keyword
                                    :validate-fn [#{:batch :continuous}]
                                    :validate-msg ["Invalid run mode"]]
                                   ["-p" "--poll-interval SECONDS" "Polling interval"
                                    :parse-fn #(Integer/parseInt %)]]
                        :required []
                        :opt-spec ::ss/runner-opts
                        :run-fn   r/run-with-opts}})

(defn- command-opts [cmd]
  (-> (get COMMANDS cmd)
      :opts
      (into global-opts)))

(defn- command-list []
  (->> COMMANDS
       (map (fn [[k {:keys [desc]}]] (format "  %s  %s" (name k) desc)))
       (str/join "\n")))

(defn- usage [cmd]
  (if cmd
    ;; Sub-command usage
    (let [{:keys [args]} (get COMMANDS cmd)]
      (format "usage: beq %s [options] %s\n\nOptions:\n%s\n"
              (name cmd) args
              (:summary (c/parse-opts [] (command-opts cmd)))))

    ;; Top-level usage
    (format "usage: beq <command> [options]\n\nCommands:\n%s\n\nOptions:\n%s\n"
            (command-list)
            (:summary (c/parse-opts [] global-opts)))))

(defn die []
  (if (in-repl?) (throw (RuntimeException. "pretending to die in REPL"))
                 (System/exit 1)))

(defn die-with-usage
  ([cmd] (println (usage cmd)) (die))
  ([cmd fmt & args]
   (println "ERROR:" (apply format fmt args) "\n")
   (die-with-usage cmd)))

(defn- check-required [cmd options]
  (when-let [missing (->> (get COMMANDS cmd)
                          :required
                          (filter #(not (contains? options %)))
                          (map #(format "  --%s" (name %)))
                          (str/join "\n")
                          not-empty)]
    (die-with-usage cmd "Missing required arguments:\n%s" missing)))

(defn load-conf [path]
  (when path
    (log/debugf "Loading config file: %s" path)
    (->> path slurp yaml/parse-string)))

(defn -main [& args]
  (let [[cmd-name & cmd-args] args
        cmd (keyword cmd-name)
        {:keys [run-fn opt-spec]} (get COMMANDS cmd)
        {:keys [options arguments errors]} (->> cmd command-opts (c/parse-opts cmd-args))
        {:keys [help conf]} options]
    (log/merge-config! {:level :debug})
    (check-required cmd options)
    (cond (nil? cmd-name) (die-with-usage nil)
          (nil? run-fn) (die-with-usage nil)
          errors (die-with-usage cmd (str/join "\n" errors))
          help (die-with-usage cmd)
          :else (let [opts (-> conf load-conf (merge options))]
                  (run-fn (conform-or-throw opt-spec "Invalid configuration options" opts)
                          arguments)))))
