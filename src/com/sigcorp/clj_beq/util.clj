(ns com.sigcorp.clj-beq.util
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as exp])
  (:import (java.util UUID)
           (java.security MessageDigest)))

(defn uuid [] (str (UUID/randomUUID)))

(defn md5 [s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))

(defn trunc
  "returns s truncated, if necessary, to n characters"
  [n s]
  (subs s 0 (min (count s) n)))

(defn conform-or-throw
  "returns (spec/conform spec x) when valid, throws an ex-info with msg & args if not"
  [spec msg x]
  (let [res (s/conform spec x)]
    (case res ::s/invalid
              (throw (ex-info (str msg "\n" (exp/expound-str spec x)) {:spec spec :x x}))
              res)))
