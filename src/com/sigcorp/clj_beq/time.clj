(ns com.sigcorp.clj-beq.time
  (:import (java.time.format DateTimeFormatter)
           (java.time OffsetTime LocalTime ZoneId ZoneOffset Instant)
           (java.time.temporal ChronoField)))

(defn offset [o]
  (cond
    (instance? ZoneOffset o) o
    (instance? String o) (offset (ZoneId/of o))
    (instance? ZoneId o) (-> o .getRules (.getOffset (Instant/now)))))

(defn offset-time
  ([s] (offset-time s (ZoneId/systemDefault)))
  ([s default-tz]
   (let [t (. (DateTimeFormatter/ISO_TIME) (parse s))]
     (if (.isSupported t ChronoField/OFFSET_SECONDS)
       (OffsetTime/from t)
       (-> (LocalTime/from t)
           (OffsetTime/of (offset (or default-tz (ZoneId/systemDefault)))))))))
