(ns com.sigcorp.clj-beq.spec-test
  (:require [com.sigcorp.clj-beq.spec :refer :all]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]))

(deftest exercise-specs
  (let [spec-ns "com.sigcorp.clj-beq.spec"]
    (->> (s/registry)
         (filter (fn [[k _]] (= spec-ns (namespace k))))
         (filter (fn [[_ v]] (s/spec? v)))
         (map first)
         (map (fn [k]
                (testing
                  (format "exercising %s" k)
                  (s/exercise k))))
         doall)))