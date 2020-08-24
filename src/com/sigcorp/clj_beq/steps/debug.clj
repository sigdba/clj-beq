(ns com.sigcorp.clj-beq.steps.debug
  (:require [com.sigcorp.clj-beq.util :refer [dump-var]]
            [com.sigcorp.clj-beq.templates :refer [expand]]
            [taoensso.timbre :as log]))

(defn dump-step
  "returns a step function which dumps the event structure to the log"
  [spec]
  (let [{:keys [label] :or {label "DUMP"}} spec
        level :debug]
    (fn [event]
      (dump-var level label event)
      {:step-status :success})))

(defn debug-step
  "returns a step function which prints a message to the log"
  [spec]
  (let [{:keys [message]} spec
        level :debug]
    (fn [event]
      (log/log level (expand message event))
      {:step-status :success})))
