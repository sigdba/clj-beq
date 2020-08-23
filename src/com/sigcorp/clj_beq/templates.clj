(ns com.sigcorp.clj-beq.templates
  (:require [selmer.parser :refer [render known-variables]]))

(defn expand-template
  ([s vars] (expand-template s vars 0))
  ([s vars depth]
   (when (> depth 100) (throw (ex-info "Max depth exceeded expanding variables" {:vars vars :s s :depth depth})))
   (if (empty? (known-variables s))
     s
     (expand-template (render s vars) vars (inc depth)))))

(defn expand
  [o vars]
  (cond
    (string? o) (expand-template o vars)
    (map? o) (->> o seq
                  (map (fn [[k v]] [k (expand v vars)]))
                  (into {}))
    (seqable? o) (->> o (map #(expand % vars))
                      (into (empty o)))
    :else o))
