(ns com.sigcorp.clj-beq.db
  "utility methods for JDBC databases"
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [taoensso.timbre :as log])
  (:import (java.sql Connection Types CallableStatement)))

(defn query [db & args]
  "returns the results of a query `(first args)` with optional parameters `(rest args)` on `db`"
  (log/tracef "Query: \n  %s" (first args))
  (apply j/query db args))

(defn update! [db & args]
  (log/tracef "Update: \n %s" args)
  (apply j/update! db args))

(defn execute! [db & args]
  "executes the statement `(first args)` with optional parameters `(rest args)` on `db` and returns the result"
  (log/tracef "Execute: \n %s" args)
  (let [ret (apply j/execute! db args)]
    (log/tracef "Execute Completed: %s" ret)
    ret))

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

(defn wait-on-alert
  [db alert-name timeout]
  (j/with-db-connection [conn-map db]
    (let [^Connection conn (:connection conn-map)]
      (j/db-do-prepared conn-map ["{ call dbms_alert.register(?, true) }" alert-name])
      (with-open [stm (doto (.prepareCall conn "{ call dbms_alert.waitone(?, ?, ?, ?) }")
                        (.setString 1 alert-name)
                        (.registerOutParameter 2 Types/VARCHAR)
                        (.registerOutParameter 3 Types/INTEGER)
                        (.setInt 4 timeout)
                        (.executeUpdate))]
        (case (.getInt stm 3)
          0 :success
          1 :timeout)))))

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
