(ns com.sigcorp.clj-beq.templates
  (:require [selmer.parser :refer [render known-variables]]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(defn keywordize
  "If string `s` can be read as a Clojure keyword, return it as a keyword, otherwise return `s` unchanged."
  [s]
  (try
    (let [v (->> s str/trim edn/read-string)]
      (if (keyword? v) v s))
    (catch Exception _ s)))

(defn expand-template
  "Recursively expands variables in the string `s` from the map `vars`. Strings matching the syntax of keywords will
  also be converted to keywords."
  ([s vars] (expand-template s vars 0))
  ([s vars depth]
   (cond
     (> depth 100) (throw (ex-info "Max depth exceeded expanding variables" {:vars vars :s s :depth depth}))
     (empty? (known-variables s)) (keywordize s)
     :else (expand-template (render s vars) vars (inc depth)))))

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
