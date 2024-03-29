(ns com.sigcorp.clj-beq.events
  (:use [com.sigcorp.clj-beq.db]
        [com.sigcorp.clj-beq.util])
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [com.sigcorp.clj-beq.spec :as ss]))

(def ^:private RENAMES {:eqts-code :system-code
                        :eqnm-code :event-code})

(defn- short-column-name
  "Given a Banner-style column keyword `k` (e.g. `:goreqrc_seqno`) returns a friendlier keyword (e.g. `:seqno`)"
  [k]
  (->> (-> k name (str/split #"_"))                         ; Split the keyword into a list of strings on '_'
       rest                                                 ; Drop the first part which is usually a table name.
       (str/join "-")                                       ; Re-join the string with dashes.
       keyword))                                            ; Return as a keyword.

(defn- nice-row
  "Returns a row map from `row` with columns renamed to match the spec"
  [row]
  (->> row
       (filter second)
       (map (fn [[k v]] [(short-column-name k) v]))
       (map (fn [[k v]] [(get RENAMES k k) v]))
       (into {})))

(defn- get-event-data
  "Returns the event data map from GOREQRC for the event with ID `seqno`"
  [db seqno]
  (->> (query db ["select * from GOREQRC where goreqrc_seqno=?" seqno]
              {:row-fn nice-row})
       (map (fn [{:keys [parm-name parm-value]}] [parm-name parm-value]))
       (into {})))

(defn- event-kw-to-col
  "Returns the appropriate column and operator of a where clause for the given keyword."
  [k]
  (case k
    :max-rows "rownum<="
    (str "gobeqrc_" (-> k name (str/replace #"-" "_")) "=")))

(defn- event-where-with [& args]
  (->> args
       (partition 2)
       (map (fn [[k v]] [(event-kw-to-col k) v]))
       (reduce into [])
       (apply where-with)))

(defn- row-to-event
  [db get-data row]
  (let [{:keys [seqno]} row
        data-fn (if get-data #(get-event-data db seqno) (constantly nil))]
    (valid-or-throw ::ss/event "Received malformed event"
                    (-> row
                        (assoc :data (data-fn))
                        (assoc :db db)))))

(defn get-events
  "returns a seq of records from GOBEQRC for the given system and, optionally, event code"
  [db opts system-code event-code status]
  (let [{:keys [max-rows get-data user-id] :or {max-rows 1 get-data true user-id nil}} opts
        [where & binds] (event-where-with :eqts-code system-code
                                          :eqnm-code event-code
                                          :status-ind status
                                          :max-rows max-rows
                                          :user-id user-id)
        q (into [(str "select * from GOBEQRC where " where)] binds)]
    (->> (query db q {:row-fn nice-row})
         (map #(row-to-event db get-data %)))))

(defn update-event-status! [db user-id status-ind & wheres]
  (log/tracef "Updating events: %s" wheres)
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

;(defn update-event-data! [db seqno data]
;  (loop [[[kk vv] & rest] data]
;    (let [k (str kk)
;          v (str vv)]
;      (execute! db
;                ["merge into goreqrc using dual on (GOREQRC_SEQNO=? and GOREQRC_PARM_NAME=?)
;                when matched then update set GOREQRC_PARM_VALUE=?
;                when not matched then insert
;                       (GOREQRC_SEQNO, GOREQRC_PARM_SEQNO, GOREQRC_PARM_NAME, GOREQRC_PARM_VALUE, GOREQRC_USER_ID,
;                        GOREQRC_ACTIVITY_DATE)
;                values (?, general.gobeseq.nextval, ?, ?, user, sysdate)"
;
;                 seqno k v seqno k v]))
;    (when rest (recur rest))))

(defn default-claiming-user-fn []
  (->> (uuid)
       md5
       (take 30)
       (apply str)))

(defn claim-events!
  [db opts system-code event-code]
  (let [{:keys [max-rows claimable-status claimed-status claiming-user-fn]
         :or   {max-rows         1
                claimable-status "0"
                claimed-status   "1"
                claiming-user-fn default-claiming-user-fn}} opts
        claiming-user (claiming-user-fn)
        update-count (-> (update-event-status! db claiming-user claimed-status
                                               :eqts-code system-code
                                               :eqnm-code event-code
                                               :status-ind claimable-status
                                               :max-rows max-rows)
                         first)]
    (if (< update-count 1)
      ; If the update affected no rows, don't bother querying.
      []
      (get-events db
                  (assoc opts :user-id claiming-user)
                  system-code event-code claimed-status))))

(defn require-parm [event parm]
  (let [{:keys [seqno data]} event
        ret (get data parm)]
    (if ret ret
            (throw (ex-info (format "Event %s missing required parameter '%s'" seqno parm)
                            {:event         event
                             :no-stacktrace true})))))