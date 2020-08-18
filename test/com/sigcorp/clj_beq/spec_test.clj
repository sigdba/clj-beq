(ns com.sigcorp.clj-beq.spec-test
  (:require [com.sigcorp.clj-beq.spec :refer :all]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.string :as str]))

(deftest exercise-specs
  (let [spec-ns "com.sigcorp.clj-beq.spec"]
    (->> (s/registry)                                       ; Start with the full spec registry.
         (filter (fn [[k _]] (= spec-ns (namespace k))))    ; Filter it down to our namespace.
         (filter (fn [[_ v]] (s/spec? v)))                  ; Keep just the specs.
         (map first)                                        ; Extract the name of the spec.
         (map (fn [k]                                       ; Exercise the spec.
                (testing
                  (format "exercising %s" k)
                  (s/exercise k))))
         doall)))