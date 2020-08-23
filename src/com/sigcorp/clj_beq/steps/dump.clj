(ns com.sigcorp.clj-beq.steps.dump
  (:require [taoensso.timbre :as log]
            [clojure.pprint :refer [pprint]]))

(def RULE (->> (repeat 80 "-") (apply str)))

(defn dump-step
  "returns a step function which dumps the event structure to the log"
  [spec]
  (let [{:keys [label]} spec
        label-s (if label (str " " label) "")]
    (fn [event]
      (log/infof "DUMP %s\n%s\n%s%s" label-s RULE (with-out-str (pprint event)) RULE)
      {:step-status :success})))
