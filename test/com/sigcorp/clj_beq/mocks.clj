(ns com.sigcorp.clj-beq.mocks)

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
