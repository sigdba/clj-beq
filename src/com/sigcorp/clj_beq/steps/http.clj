(ns com.sigcorp.clj-beq.steps.http
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [com.sigcorp.clj-beq.spec :as ss]
            [com.sigcorp.clj-beq.templates :refer [expand]]
            [taoensso.timbre :as log])
  (:use [com.sigcorp.clj-beq.util]))

(defn- method-named [method]
  (let [ret (->> method
                 str/lower-case
                 (str "clj-http.client/")
                 symbol
                 resolve)]
    (when-not ret (throw (ex-info (format "invalid HTTP method: %s" method) {})))
    ret))

(defn http-step
  [spec]
  (let [opts (conform-or-throw ::ss/http-opts "Invalid or missing http settings" spec)]
    (fn [event]
      (let [{:keys [method url request]
             :or   {request {}}} (expand opts event)]
        (log/debugf "Invoking HTTP %s %s %s" method url request)
        (let [res ((method-named method) url request)]
          {:url      url
           :request  request
           :response res
           :step-status :success})))))
