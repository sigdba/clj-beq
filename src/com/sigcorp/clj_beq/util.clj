(ns com.sigcorp.clj-beq.util
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