(ns com.sigcorp.clj-beq.cli
  (:require [com.sigcorp.clj-beq.runner :as r]
            [clojure.tools.cli :as c]
            [taoensso.timbre :as log]
            [clojure.string :as str])
  (:use [com.sigcorp.clj-beq.util])
  (:gen-class))

(def global-opts [["-h" "--help" "prints this screen"]])

(def COMMANDS {:runner {:desc     "Process events"
                        :args     "-c CONF"
                        :opts     [["-c" "--conf CONF" "Configuration file"]
                                   ["-p" "--poll-interval SECONDS" "Polling interval"
                                    :default 30
                                    :parse-fn #(Integer/parseInt %)]]
                        :required [:conf]
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

(defn -main [& args]
  (let [[cmd-name & cmd-args] args
        cmd (keyword cmd-name)
        {:keys [run-fn]} (get COMMANDS cmd)
        {:keys [options arguments errors]} (->> cmd command-opts (c/parse-opts cmd-args))
        {:keys [help]} options]
    (log/merge-config! {:level :debug})
    (check-required cmd options)
    (cond (nil? cmd-name) (die-with-usage nil)
          (nil? run-fn) (die-with-usage nil)
          errors (die-with-usage cmd (str/join "\n" errors))
          help (die-with-usage cmd)
          :else (run-fn options arguments))))
