(ns com.sigcorp.clj_beq.clj-beq
  (:use [com.sigcorp.clj_beq.db]
        [com.sigcorp.clj-beq.util])
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s])
  (:import (java.util Date)))

(s/def ::numberish (s/or :number number?
                         :number-string (s/and string? #(re-matches #"^[0-9]+$" %))))
(s/def ::date #(instance? Date %))

(s/def ::seqno number?)
(s/def ::eqts-code string?)
(s/def ::eqnm-code string?)
(s/def ::status-ind ::numberish)
(s/def ::user-id string?)
(s/def ::activity-date ::date)
(s/def ::surrogate-id number?)
(s/def ::version number?)
(s/def ::data-origin string?)
(s/def ::vpdi-code string?)
(s/def ::data (s/map-of string? string?))

(s/def ::event (s/keys :req-un [::seqno ::eqts-code ::eqnm-code ::status-ind ::activity-date]
                       :opt-un [::user-id ::surrogate-id ::version ::data-origin ::vpdi-code ::data]))

(def DB (db-with "jdbc:oracle:thin:@bag.sigcorp.com:11015:SMPL" "system" com.sigcorp.clj-beq/PW))

(defn short-column-name [k]
  (->> (-> k name (str/split #"_"))
       rest
       (str/join "-")
       keyword))

(defn nice-row [row]
  (->> row
       (filter second)
       (map (fn [[k v]] [(short-column-name k) v]))
       (into {})))

(defn get-event-data [db seqno]
  (->> (query db ["select * from GOREQRC where goreqrc_seqno=?" seqno]
              {:row-fn nice-row})
       (map (fn [{:keys [parm-name parm-value]}] [parm-name parm-value]))
       (into {})))

(defn where-with [& args]
  (let [pairs (->> args
                   (partition 2)
                   (filter second))
        clause (->> pairs
                    (map first)
                    (map #(str % "?"))
                    (str/join " and "))
        binds (->> pairs
                   (map second)
                   vec)]
    (into [clause] binds)))

(defn- event-where-with [eqts-code eqnm-code status-ind max-rows user-id]
  (where-with "gobeqrc_eqts_code=" eqts-code
              "gobeqrc_eqnm_code=" eqnm-code
              "gobeqrc_status_ind=" status-ind
              "gobeqrc_user_id=" user-id
              "rownum<=" max-rows))

(defn get-events
  "returns a seq of records from GOBEQRC for the given system and, optionally, event code"
  [db
   {:keys [max-rows get-data user-id] :or {max-rows 1 get-data true user-id nil}}
   system-code event-code status]
  (let [[where & binds] (event-where-with system-code event-code status max-rows user-id)
        q (into [(str "select * from GOBEQRC where " where)] binds)
        rows (query db q {:row-fn nice-row})]
    (if get-data (map (fn [{:keys [seqno] :as row}] (assoc row :data (get-event-data db seqno))) rows)
                 rows)))

(defn default-claiming-user-fn []
  (->> (uuid)
       md5
       (take 30)
       (apply str)))

(defn claim-events!
  [db
   {:keys [max-rows claimable-status claimed-status claiming-user-fn]
    :or   {max-rows         1
           claimable-status "0"
           claimed-status   "1"
           claiming-user-fn default-claiming-user-fn}
    :as   opts}
   system-code event-code]
  (let [claiming-user (claiming-user-fn)
        [where & binds] (event-where-with system-code event-code claimable-status max-rows nil)
        update (str "update GOBEQRC set gobeqrc_status_ind=?, gobeqrc_user_id=?, gobeqrc_activity_date=sysdate where "
                    where)
        args (into [claimed-status claiming-user] binds)]
    (execute! db (into [update] args))
    (get-events db (assoc opts :user-id claiming-user) system-code event-code claimed-status)))

(claim-events! DB {} "CLJ" nil)

#_(get-events DB {} "CLJ" nil "0")