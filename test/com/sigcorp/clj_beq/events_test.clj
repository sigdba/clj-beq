(ns com.sigcorp.clj-beq.events-test
  (:require [clojure.test :refer :all]
            [com.sigcorp.clj-beq.events :refer :all]
            [com.sigcorp.clj-beq.mocks :refer :all]
            [clojure.string :as str]))

;; Ok. This has gotten silly. Basically writing a terrible SQL engine. Probably best just to use in-memory Derby for
;; unit testing.
(defn parse-query [query]
  (let [[sql & bind-vals] query
        m (re-matcher #"^select .* from ([^ ]+) (where ((([a-z0-9_]+)([=><!]+)\?( +and +)?)+))" sql)
        [_ table _ where & _] (re-find m)]
    (->> (str/split where #" +and +")                       ; split the where clause by ' and '
         (map #(re-find #"^([a-z0-9_]+)([=><!]+)\?$" %))    ; break each op into it's components
         (map rest)                                         ; drop the match string, now have [col op]
         (map #(into [%1] %2) bind-vals)                    ; zip in the binding values, now have [val col op]
         (map (fn [[val col op]]
                [(keyword col)                              ; convert the column name to a keyword
                 (->> op symbol resolve)                    ; convert the operator to the equivalent function
                 val]))
         (into [(-> table str/lower-case keyword)]))))      ; return with the table as a keyword

(defn- row-filter [col op-fn value]
  (fn [row] (-> row col (op-fn value))))

(let [query ["select * from GOBEQRC where gobeqrc_eqts_code=? and gobeqrc_eqnm_code=? and gobeqrc_status_ind=? and rownum<=?" "SYSTEM" "EVENT" 1 1]
      data {:tables {:gobeqrc [{:gobeqrc_seqno      0
                                :gobeqrc_eqts_code  "SYSTEM1"
                                :gobeqrc_eqnm_code  "EVENT1"
                                :gobeqrc_status_ind 0}]}}]
  (let [[table & all-wheres] (parse-query query)
        is-rownum? #(->> % first (= :rownum))
        outer-wheres (filter is-rownum? all-wheres)
        inner-wheres (remove is-rownum? all-wheres)
        outer-filter (->> outer-wheres (map #(apply row-filter %)) (apply comp))
        inner-filter (->> inner-wheres (map #(apply row-filter %)) (apply comp))]
    (->> data :tables table)))

(let [db-fn (fn [prev cmd _ & args] (printf "DB Called with: %s\n" args))
      [*args* db] (mock-db db-fn true)]
  (get-events db {} "SYSTEM" "EVENT" 1))
