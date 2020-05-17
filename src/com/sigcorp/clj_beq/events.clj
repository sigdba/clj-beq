(ns com.sigcorp.clj_beq.events
  (:use [com.sigcorp.clj_beq.db]
        [com.sigcorp.clj-beq.util])
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log])
  (:import (java.util Date)))

(s/def ::date #(instance? Date %))

(s/def ::seqno number?)
(s/def ::eqts-code string?)
(s/def ::eqnm-code string?)
(s/def ::status-ind (s/and string? #(= 1 (count %))))
(s/def ::user-id (s/and string? #(<= 30 (count %))))
(s/def ::activity-date ::date)
(s/def ::surrogate-id number?)
(s/def ::version number?)
(s/def ::data-origin string?)
(s/def ::vpdi-code string?)
(s/def ::data (s/map-of string? string?))

(s/def ::event (s/keys :req-un [::seqno ::eqts-code ::eqnm-code ::status-ind ::activity-date]
                       :opt-un [::user-id ::surrogate-id ::version ::data-origin ::vpdi-code ::data]))

(defn- short-column-name [k]
  (->> (-> k name (str/split #"_"))
       rest
       (str/join "-")
       keyword))

(defn- nice-row [row]
  (->> row
       (filter second)
       (map (fn [[k v]] [(short-column-name k) v]))
       (into {})))

(defn- get-event-data [db seqno]
  (->> (query db ["select * from GOREQRC where goreqrc_seqno=?" seqno]
              {:row-fn nice-row})
       (map (fn [{:keys [parm-name parm-value]}] [parm-name parm-value]))
       (into {})))

(defn- event-kw-to-col [k]
  "returns the appropriate column and operator of a where clause for the given keyword"
  (case k
    :max-rows "rownum<="
    (str "gobeqrc_" (-> k name (str/replace #"-" "_")) "=")))

(defn- event-where-with [& args]
  (->> args
       (partition 2)
       (map (fn [[k v]] [(event-kw-to-col k) v]))
       (reduce into [])
       (apply where-with)))

(defn get-events
  "returns a seq of records from GOBEQRC for the given system and, optionally, event code"
  [db
   {:keys [max-rows get-data user-id] :or {max-rows 1 get-data true user-id nil}}
   system-code event-code status]
  (let [[where & binds] (event-where-with :eqts-code system-code
                                          :eqnm-code event-code
                                          :status-ind status
                                          :max-rows max-rows
                                          :user-id user-id)
        q (into [(str "select * from GOBEQRC where " where)] binds)
        rows (query db q {:row-fn nice-row})]
    (if get-data (map (fn [{:keys [seqno] :as row}] (assoc row :data (get-event-data db seqno))) rows)
                 rows)))

(defn update-event-status! [db user-id status-ind & wheres]
  (log/debugf "Updating events: %s" wheres)
  (let [[where & binds] (apply event-where-with wheres)
        update (str "update GOBEQRC set gobeqrc_status_ind=?, gobeqrc_user_id=?, gobeqrc_activity_date=sysdate where "
                    where)
        args (into [status-ind user-id] binds)]

    ;; Make sure that at least one where-clause is provided so that we don't try to update
    ;; the whole event queue.
    (if (< (count binds) 1)
      (throw (ex-info "Refusing to update all events in GOBEQRC. You must specify at least one where clause."
                      {:user-id    user-id
                       :status-ind status-ind
                       :wheres     wheres
                       :binds      binds
                       :update     update
                       :args       args}))
      (execute! db (into [update] args)))))

(defn update-event-data! [db seqno data]
  (loop [[[kk vv] & rest] data]
    (let [k (str kk)
          v (str vv)]
      (execute! db
                ["merge into goreqrc using dual on (GOREQRC_SEQNO=? and GOREQRC_PARM_NAME=?)
                when matched then update set GOREQRC_PARM_VALUE=?
                when not matched then insert
                       (GOREQRC_SEQNO, GOREQRC_PARM_SEQNO, GOREQRC_PARM_NAME, GOREQRC_PARM_VALUE, GOREQRC_USER_ID,
                        GOREQRC_ACTIVITY_DATE)
                values (?, general.gobeseq.nextval, ?, ?, user, sysdate)"

                 seqno k v seqno k v]))
    (when rest (recur rest))))

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
  (let [claiming-user (claiming-user-fn)]
    (update-event-status! db claiming-user claimed-status
                          :eqts-code system-code
                          :eqnm-code event-code
                          :status-ind claimable-status
                          :max-rows max-rows)
    (get-events db (assoc opts :user-id claiming-user) system-code event-code claimed-status)))