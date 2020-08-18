(ns com.sigcorp.clj-beq.db
  "utility methods for JDBC databases"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [taoensso.timbre :as log])
  (:import (java.sql Connection Types)
           (java.util Map)))

;; TODO: It's kinda ugly to have a function this complex as a protocol method. There should be a clean way to rewrite it
;;       such that it relies on lower-level jdbc functions provided by the protocol.
(defn- -wait-on-alert
  [db alert-name timeout]
  (jdbc/with-db-connection [conn-map db]
    (let [^Connection conn (:connection conn-map)]
      (jdbc/db-do-prepared conn-map ["{ call dbms_alert.register(?, true) }" alert-name])
      (with-open [stm (doto (.prepareCall conn "{ call dbms_alert.waitone(?, ?, ?, ?) }")
                        (.setString 1 alert-name)
                        (.registerOutParameter 2 Types/VARCHAR)
                        (.registerOutParameter 3 Types/INTEGER)
                        (.setInt 4 timeout)
                        (.executeUpdate))]
        (case (.getInt stm 3)
          0 :success
          1 :timeout)))))

(defprotocol Jdbcish
  "Provides core JDBC-style functions. We're defining it as a protocol so that it can be mocked during testing."
  (jdbc-query [db sql-params opts])
  (jdbc-execute! [db sql-params opts])
  (wait-on-alert [db alert-name timeout]))

(extend Map
  Jdbcish
  {:jdbc-query jdbc/query
   :jdbc-execute! jdbc/execute!
   :wait-on-alert -wait-on-alert})

(defn query
  ([db sql-params] (query db sql-params {}))
  ([db sql-params opts]
   (log/tracef "Query: \n  %s" sql-params)
   (jdbc-query db sql-params opts)))

(defn execute!
  ([db sql-params] (execute! db sql-params {}))
  ([db sql-params opts]
   (log/tracef "Execute: \n %s" sql-params)
   (let [ret (jdbc-execute! db sql-params opts)]
     (log/tracef "Execute Completed: %s" ret)
     ret)))

(defn purge-pipe!
  "purges all messages in `pipe-name`"
  [db pipe-name]
  (execute! db ["{ call dbms_pipe.purge(?) }" pipe-name]))

(defn wait-on-pipe!
  "Blocks up to `timeout` seconds waiting for a message on `pipe-name`.
  Returns :success if a message is received, :timeout on timeout and :error if an error or interrupt occurs. If
  `purge-after` is true then [[purge-pipe!]] is called prior to returning :success."
  [db pipe-name timeout purge-after]
  (case (->> (query db ["select dbms_pipe.receive_message(?, ?) r from dual" pipe-name timeout])
             first :r int)
    0 (do (when purge-after (purge-pipe! db pipe-name))
          :success)
    1 :timeout
    :error))

(defn db-with [url user pass]
  {:connection-uri url
   :user           user
   :password       pass})

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
