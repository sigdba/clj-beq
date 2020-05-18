(ns com.sigcorp.clj-beq.cli
  (:require [clojure.tools.cli :as c]
            [com.sigcorp.clj-beq.runner :as r])
  (:gen-class))

(def cli-options
  [])

(defn -main [& args]
  (let [{:keys [options arguments]} (c/parse-opts args cli-options)
        [conf-path] arguments
        runner (r/runner-with-conf-path conf-path)]
    (runner)))
