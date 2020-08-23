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

;; We'll write a few option apply-ers this way until a pattern emerges which we can abstract
(defn- insert-opt-body-as
  "Returns `req-opts` with `:as` specified if `:body-as` is present in `spec`"
  [spec req-opts]
  (let [v (get spec :body-as)]
    (if v (assoc req-opts :as (keyword v))
          req-opts)))

(defn- insert-opts
  [spec req-opts]
  (->> req-opts
       (insert-opt-body-as spec)))

(defn http-step
  [spec]
  (let [opts (conform-or-throw ::ss/http-opts "Invalid or missing http settings" spec)]
    (fn [event]
      (let [{:keys [method url request]
             :or   {request {}}} (expand opts event)
            req-opts (insert-opts opts request)]
        (log/debugf "Invoking HTTP %s %s %s" method url req-opts)
        (let [res ((method-named method) url req-opts)]
          {:url      url
           :request  req-opts
           :response res
           :step-status :success})))))
