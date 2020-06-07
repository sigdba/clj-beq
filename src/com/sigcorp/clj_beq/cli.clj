(ns com.sigcorp.clj-beq.cli
  (:require [com.sigcorp.clj-beq.runner :as r]
            [com.sigcorp.clj-beq.spec :as ss]
            [clojure.tools.cli :as c]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clj-yaml.core :as yaml])
  (:use [com.sigcorp.clj-beq.util])
  (:gen-class))

(def VERBOSITIES [:report :fatal :error :warn :info :debug :trace])
(def DEFAULT-VERBOSITY :info)

(defn- inc-verbosity [v]
  (let [vb (or v DEFAULT-VERBOSITY)]
    (or (->> VERBOSITIES
             (drop-while #(not (= vb %)))
             (drop 1)
             first)
        :trace)))

(defn- split-opt [s]
  (let [matcher (re-matcher #"^([^=]+)=(.*)$" s)]
    (re-find matcher)
    (->> (re-groups matcher)                                ; extract the match groups
         rest                                               ; drop the first one
         (apply (fn [k v] [(keyword k) v])))))              ; convert the key to a keyword

(defn- add-opt
  "assoc-fn for the -C option; parses this=that parameters and returns the updated map"
  [opts _ o]
  (apply assoc opts (split-opt o)))

(defn- add-env-opt
  "assoc-fn for the -E option; parses this=that where <that> is the name of an environment variable"
  [opts _ o]
  (let [[k v] (split-opt o)
        val (System/getenv v)]
    (when-not val (throw (ex-info (format "Cannot set '%s', missing environment variable '%s'" (name k) v) {})))
    (assoc opts k val)))

(def global-opts [["-c" "--conf CONF" "Configuration file"]
                  [:id :add-opt
                   :short-opt "-C"
                   :required "OPT"
                   :assoc-fn add-opt]
                  [:id :add-env-opt
                   :short-opt "-E"
                   :required "ENV-OPT"
                   :assoc-fn add-env-opt]
                  ["-v" nil "Verbosity level"
                   :id :verbosity
                   :update-fn inc-verbosity]
                  ["-q" nil "Quiet mode"
                   :id :verbosity
                   :update-fn (constantly :warn)]
                  ["-h" "--help" "prints this screen"]])

(def COMMANDS {:runner {:desc     "Process events"
                        :args     ""
                        :opts     [["-m" "--mode MODE" "Run mode: batch or continuous"
                                    :default :continuous
                                    :parse-fn keyword
                                    :validate-fn [#{:batch :continuous}]
                                    :validate-msg ["Invalid run mode"]]
                                   ["-p" "--poll-interval SECONDS" "Polling interval"
                                    :parse-fn #(Integer/parseInt %)]
                                   ["-d" "--enable-default-handler"]]
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

(defn- verbosity-with-opts [opts]
  (let [{:keys [verbosity log-level]} opts]
    (or verbosity log-level DEFAULT-VERBOSITY)))

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

    ;; Set the log level
    (log/merge-config! {:level (verbosity-with-opts options)})

    ;; Validate the command-line options
    (check-required cmd options)
    (cond (nil? cmd-name) (die-with-usage nil)
          (nil? run-fn) (die-with-usage nil)
          errors (die-with-usage cmd (str/join "\n" errors))
          help (die-with-usage cmd))

    ;; Load the configuration file (if any) and run the command
    (let [opts (-> conf load-conf (merge options))]
      ;; Set the log level again. It might have changed from the configuration file.
      (log/merge-config! {:level (verbosity-with-opts opts)})

      ;; Kick the pig.
      (run-fn (conform-or-throw opt-spec "Invalid configuration options" opts)
              arguments))

    ;; Shutdown any lingering agent threads
    (shutdown-agents)))
