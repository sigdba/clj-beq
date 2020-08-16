(ns com.sigcorp.clj-beq.db
  "utility methods for JDBC databases"
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn query [db & args]
  "returns the results of a query `(first args)` with optional parameters `(rest args)` on `db`"
  (log/tracef "Query: \n  %s" (first args))
  (apply j/query db args))

;(defn update! [db & args]
;  (log/tracef "Update: \n %s" args)
;  (apply j/update! db args))

(defn execute! [db & args]
  "executes the statement `(first args)` with optional parameters `(rest args)` on `db` and returns the result"
  (log/tracef "Execute: \n %s" args)
  (let [ret (apply j/execute! db args)]
    (log/tracef "Execute Completed: %s" ret)
    ret))

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
