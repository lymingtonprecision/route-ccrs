(ns route-ccrs.sql.serializers
  (:require [bugsbio.squirrel :as sq]
            [clj-time.coerce :as tc]
            [clojure.string :refer [upper-case]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(def keyword-serializer
  (reify sq/Serializer
    (serialize [_ v] v)
    (deserialize [_ v] (keyword (str v)))))

(def num-serializer
  (reify sq/Serializer
    (serialize [_ v] v)
    (deserialize [_ v]
      (cond
        (nil? v) nil
        (number? v) v
        (string? v) (cond
                      (re-find #"\." v) (java.lang.Double. v)
                      (> (count v) 9) (java.math.BigDecimal. v)
                      :else (java.lang.Integer/parseInt v))
        :else nil))))

(def int-serializer
  (reify sq/Serializer
    (serialize [_ v] v)
    (deserialize [_ v]
      (cond
        (nil? v) nil
        (number? v) (int v)
        (string? v) (cond
                      (re-find #"\." v) (.intValue (java.lang.Double. v))
                      (> (count v) 9) (java.math.BigInteger. v)
                      :else (java.lang.Integer/parseInt v))
        :else nil))))

(def date-serializer
  (reify sq/Serializer
    (serialize [_ v] (tc/to-sql-date v))
    (deserialize [_ v] (tc/to-local-date v))))

(def bool-serializer
  (reify sq/Serializer
    (serialize [_ v] (if v "Y" ""))
    (deserialize [_ v] (and (string? v) (= "Y" (upper-case v))))))
