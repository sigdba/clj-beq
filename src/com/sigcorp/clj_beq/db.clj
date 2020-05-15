(ns com.sigcorp.clj_beq.db
  (:require [clojure.java.jdbc :as j]
            [clojure.tools.logging :as log]))

(defn query [db & args]
  (log/debugf "Query: \n  %s" (first args))
  (apply j/query db args))

(defn update! [db & args]
  (log/debugf "Update: \n %s" args)
  (apply j/update! db args))

(defn execute! [db & args]
  (log/debugf "Execute: \n %s" args)
  (apply j/execute! db args))

(defn db-with [url user pass]
  {:connection-uri url
   :user           user
   :password       pass})

