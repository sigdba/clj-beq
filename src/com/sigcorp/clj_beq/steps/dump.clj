(ns com.sigcorp.clj-beq.steps.dump
  (:require [taoensso.timbre :as log]
            [clojure.pprint :refer [pprint]]))

(def RULE (->> (repeat 80 "-") (apply str)))

(def ELIDES [[:db :password]])

(defn- elide
  [event]
  (loop [elides ELIDES m event]
    (let [[e & r] elides
          new-e (if (get-in m e) (assoc-in m e :elided) e)]
      (if r (recur r new-e)
            new-e))))

(defn dump-step
  "returns a step function which dumps the event structure to the log"
  [spec]
  (let [{:keys [label]} spec
        label-s (if label (str " " label) "")]
    (fn [event]
      (log/infof "DUMP %s\n%s\n%s%s" label-s RULE (with-out-str (-> event elide pprint)) RULE)
      {:step-status :success})))
