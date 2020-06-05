(ns com.sigcorp.clj-beq.runner
  (:require [com.sigcorp.clj-beq.process :as p]
            [com.sigcorp.clj-beq.db :as db]
            [com.sigcorp.clj-beq.spec :as ss]
            [com.sigcorp.clj-beq.events :as e]
            [com.sigcorp.clj-beq.runners.twilio :as twilio]
            [com.sigcorp.clj-beq.runners.shell :as shell]
            [taoensso.timbre :as log])
  (:use [com.sigcorp.clj-beq.util]))

(defn- conform-handler-spec [defaults spec]
  (conform-or-throw ::ss/event-handler-spec "Invalid event handler spec"
                    (merge defaults spec)))

(defn handler-factory-with-spec [spec]
  (let [type (:type spec)]
    (case type
      "shell" shell/shell-handler
      "twilio" twilio/twilio-event-handler
      (throw (ex-info (str "unrecognized handler type: " type) {:spec spec})))))

(defn- handler-with-spec [conf spec]
  (let [{:keys [system-code]} conf
        opts (conform-handler-spec conf spec)
        {:keys [event-code]} opts
        factory (handler-factory-with-spec opts)]
    (p/handler-for system-code event-code (factory opts))))

(defn- handler-specs-with-conf [conf]
  (let [{:keys [enable-default-handler event-handlers]} conf]
    (into (if enable-default-handler [{}] []) event-handlers)))

(defn- handler-with-conf [conf]
  (->> (handler-specs-with-conf conf)                       ; Take the event handlers from the configuration
       (map #(handler-with-spec conf %))                    ; Convert into handler functions
       vec                                                  ; Convert to a vector to realize all handlers immediately
       p/event-dispatcher))                                 ; Return dispatch function for the handlers

(defn- db-with-conf [conf]
  (let [{:keys [db jdbc-url jdbc-user jdbc-password]} conf]
    (if db db
           (db/db-with jdbc-url jdbc-user jdbc-password))))

(defn- claim-fn-with [conf db]
  (let [{:keys [claim-fn system-code]} conf]
    (if claim-fn claim-fn
                 #(e/claim-events! db conf system-code nil))))

(defn- finalizer-with [conf db]
  (let [{:keys [finalizer]} conf]
    (if finalizer finalizer
                  (p/db-update-finalizer db))))

(defn runner-with-conf [conf]
  (let [handler (handler-with-conf conf)
        db (db-with-conf conf)]
    (fn []
      (p/process-events conf
                        (claim-fn-with conf db)
                        handler
                        (finalizer-with conf db)))))

(defn run-with-opts [conf _]
  (let [{:keys [poll-interval mode]
         :or   {poll-interval 30}} conf
        continuous (= :continuous mode)
        runner (runner-with-conf conf)]
    (loop []
      (log/debug "Fetching events...")
      (let [c (runner)]
        (log/debugf "Processed %d events" c)
        (when (and continuous (< c 1))
          (log/debugf "Napping %d seconds" poll-interval)
          (Thread/sleep (* poll-interval 1000)))
        (when continuous (recur))))))
