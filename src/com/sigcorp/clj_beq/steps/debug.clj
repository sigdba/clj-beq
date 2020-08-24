(ns com.sigcorp.clj-beq.steps.debug
  (:require [com.sigcorp.clj-beq.util :refer [dump-var]]))

(defn debug-step
  "returns a step function which dumps the event structure to the log"
  [spec]
  (let [{:keys [label] :or {label "DUMP"}} spec
        level :debug]
    (fn [event]
      (dump-var level label event)
      {:step-status :success})))
