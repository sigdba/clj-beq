(ns com.sigcorp.clj-beq.runner-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [com.sigcorp.clj-beq.runner :refer :all]
            [com.sigcorp.clj-beq.spec :as ss]
            [com.sigcorp.clj-beq.mocks :refer [mock-fn]]))

(defn- limited-claim-fn [event-count max-rows]
  (let [*events* (ref (->> event-count (s/exercise ::ss/event) (map first)))
        *call-count* (ref 0)]
    [*events*
     *call-count*
     (fn []
       (dosync
         (alter *call-count* inc)
         (let [ret (take max-rows @*events*)]
           (ref-set *events* (drop max-rows @*events*))
           ret)))]))

(deftest run-test
  (let [test-run-fn (fn [mode]
                      (let [[_ *claim-call-count* claim-fn] (limited-claim-fn 5 2)
                            [*runner-args* runner] (mock-fn (fn [& _] (count (claim-fn))))
                            [*sleeper-args* sleeper] (mock-fn)]
                        (run runner sleeper mode)
                        [@*claim-call-count* (count @*runner-args*) (count @*sleeper-args*)]))]
    (testing "single mode"
      (let [[claim-call-count runner-call-count sleep-call-count] (test-run-fn :single)]
        (is (= 1 claim-call-count))
        (is (= 1 runner-call-count))
        (is (= 0 sleep-call-count))))
    (testing "batch mode"
      (let [[claim-call-count runner-call-count sleep-call-count] (test-run-fn :batch)]
        (is (= 4 claim-call-count))
        (is (= 4 runner-call-count))
        (is (= 0 sleep-call-count))))))
