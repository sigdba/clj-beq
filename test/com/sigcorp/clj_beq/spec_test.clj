(ns com.sigcorp.clj-beq.spec-test
  (:require [com.sigcorp.clj-beq.spec :refer :all]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.string :as str]))

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

#_(make-test `com.sigcorp.clj-beq.util/current-stack-trace)

#_(->> (s/registry)
     keys
     (map namespace)
     distinct
     (filter #(str/starts-with? % "com.sigcorp.clj-beq"))
     (map symbol)
     (map (fn [s] (require s) s))
     (map stest/enumerate-namespace)
     (reduce into)
     (filter function?)
     (map (fn [f]
            (testing (format "checking %s" f)
              (let [{:keys [check-failed]} (stest/check f)]
                (is nil? check-failed))))))
