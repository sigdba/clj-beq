(ns com.sigcorp.clj-beq.mocks
  (:require [com.sigcorp.clj-beq.db :as db]))

(defn mock-fn
  "Returns `[*args* fn]` where `fn` is a function which appends its arguments to the ref `*args*` and returns
  `(apply ret-fn args)`. If `ret-fn` is not passed then `fn` will return nil. If `prepend-previous` is true then
  `@*args*` is passed as the first argument to `ret-fn`."
  ([] (mock-fn (constantly nil)))
  ([ret-fn] (mock-fn ret-fn nil))
  ([ret-fn prepend-previous]
   (let [*args* (ref [])]
     [*args*
      (fn [& args]
        (dosync
          (let [rf (if prepend-previous (partial ret-fn @*args*) ret-fn)]
            (alter *args* conj args)
            (apply rf args))))])))

(deftype Mock [mfn]
  db/Jdbcish
  (jdbc-query [db sql-params opts] (mfn :query db sql-params opts))
  (jdbc-execute! [db sql-params opts] (mfn :execute! db sql-params opts))
  (wait-on-alert [db alert-name timeout] (mfn :wait-on-alert db alert-name timeout)))

(defn mock-db
  "Returns `[*args* db]` where `db` is an object which can be used as the `db` parameter in databased-related functions.
  Low-level database calls will be appended to `*args*` and return `(apply ret-fn args)`. If `prepend-previous` is true
  then `@*args*` is passed as the first argument to `ret-fn`."
  ([ret-fn] (mock-db ret-fn nil))
  ([ret-fn prepend-previous]
   (let [[*args* mfn] (mock-fn ret-fn prepend-previous)]
     [*args* (Mock. mfn)])))
