(ns com.sigcorp.clj-beq.events-test
  (:require [clojure.test :refer :all]
            [com.sigcorp.clj-beq.events :refer :all]
            [com.sigcorp.clj-beq.util :refer [uuid]]
            [clojure.java.jdbc :as j]
            [clojure.string :as str])
  (:import (java.util Date)))

;; So this doesn't work because Derby doesn't support Oracle's ROWNUM column.
(defn- empty-banner-db []
  ;; TODO: This is creating an in-memory DB which won't go away until the JRE closes. Add some cleanup to the process.
  (let [db {:connection-uri (format "jdbc:derby:memory:%s;create=true" (uuid))}]
    (let [ddl [[:gobeqrc [[:gobeqrc_seqno "INT PRIMARY KEY"]
                          [:gobeqrc_eqts_code "VARCHAR(10)"]
                          [:gobeqrc_eqnm_code "VARCHAR(20)"]
                          [:gobeqrc_status_ind "VARCHAR(1)"]
                          [:gobeqrc_user_id "VARCHAR(30)"]
                          [:gobeqrc_activity_date "DATE"]
                          [:gobeqrc_surrogate_id "INT"]
                          [:gobeqrc_version "INT"]
                          [:gobeqrc_data_origin "VARCHAR(30)"]
                          [:gobeqrc_vpdi_code "VARCHAR(6)"]]]
               [:goreqrc [[:goreqrc_seqno "INT"]
                          [:goreqrc_parm_seqno "INT"]
                          [:goreqrc_parm_name "VARCHAR(100)"]
                          [:goreqrc_parm_value "VARCHAR(2000)"]
                          [:goreqrc_user_id "VARCHAR(30)"]
                          [:goreqrc_activity_date "DATE"]
                          [:goreqrc_surrogate_id "INT"]
                          [:goreqrc_version "INT"]
                          [:goreqrc_data_origin "VARCHAR(30)"]
                          [:goreqrc_vpdi_code "VARCHAR(6)"]]]]]
      (->> ddl
           (map #(apply j/create-table-ddl %))
           (map (fn [ddl] (println ddl) (j/db-do-commands db ddl)))
           doall)
      db)))

(defn- insert-gobeqrc!
  [db seqno eqts-code eqnm-code status-ind]
  (j/insert! db :gobeqrc {:gobeqrc_seqno         seqno
                          :gobeqrc_eqts_code     eqts-code
                          :gobeqrc_eqnm_code     eqnm-code
                          :gobeqrc_status_ind    status-ind
                          :gobeqrc_user_id       "USER1"
                          :gobeqrc_activity_date (Date.)
                          :gobeqrc_surrogate_id  0
                          :gobeqrc_version       0
                          :gobeqrc_data_origin   "ORIGIN1"
                          :gobeqrc_vpdi_code     "VPDI1"}))

(defn- insert-goreqrc!
  [db seqno parm_seqno parm_name parm_value]
  (j/insert! db :goreqrc {:goreqrc_seqno         seqno
                          :goreqrc_parm_seqno    parm_seqno
                          :goreqrc_parm_name     parm_name
                          :goreqrc_parm_value    parm_value
                          :goreqrc_user_id       "USER1"
                          :goreqrc_activity_date (Date.)
                          :goreqrc_surrogate_id  0
                          :goreqrc_version       0
                          :goreqrc_data_origin   "ORIGIN1"
                          :goreqrc_vpdi_code     "VPDI1"}))

(defn- banner-testing-db []
  (let [db (empty-banner-db)
        gobeqrc-rows [[1 "SYSTEM1" "EVENT1" 0]
                      [2 "SYSTEM1" "EVENT1" 0]
                      [3 "SYSTEM1" "EVENT1" 1]
                      [4 "SYSTEM1" "EVENT1" 1]]
        goreqrc-rows [[1 1 "PARM1" "VAL11"]
                      [1 2 "PARM2" "VAL12"]]]
    (->> gobeqrc-rows (map #(apply insert-gobeqrc! db %)) doall)
    (->> goreqrc-rows (map #(apply insert-goreqrc! db %)) doall)
    db))

(let [db (banner-testing-db)
      events (get-events db {} "SYSTEM1" nil nil)]
  events)