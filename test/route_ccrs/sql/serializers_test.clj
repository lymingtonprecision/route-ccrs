(ns route-ccrs.sql.serializers-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.generators :as gen']
            [bugsbio.squirrel :refer [serialize deserialize]]
            [route-ccrs.sql.serializers :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; keyword-serializer

(defspec keyword-deserialization-of-strings
  (prop/for-all [s gen/string]
                (is (= (keyword s) (deserialize keyword-serializer s)))))

(defspec keyword-deserialization-of-numbers
  (prop/for-all [n gen/int]
                (is (= (keyword (str n)) (deserialize keyword-serializer n)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; int-serializer

(deftest int-deserialization-of-nil
  (is (nil? (deserialize int-serializer nil))))

(defspec int-deserialization-of-numbers
  (prop/for-all [n (gen/fmap #(+ % (rand)) gen/int)]
                (is (= (int n) (deserialize int-serializer n)))))

(defspec int-deserialization-of-non-numeric-strings
  (prop/for-all [s (gen/such-that #(nil? (re-find #"^\d+$" %))
                                  gen/string-alphanumeric)]
                (is (instance? NumberFormatException
                               (try (deserialize int-serializer s)
                                    (catch NumberFormatException e e))))))

(defspec int-deserialization-of-integer-strings
  (prop/for-all [s (gen'/string-from-regex #"\d+")]
                (is (= (if (> (count s) 9)
                         (java.math.BigInteger. s)
                         (java.lang.Integer/parseInt s))
                       (deserialize int-serializer s)))))

(defspec int-deserialization-of-float-strings
  (prop/for-all [s (gen'/string-from-regex #"\d+\.\d+")]
                (is (= (.intValue (java.lang.Double. s))
                       (deserialize int-serializer s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; bool-serializer

(deftest bool-deserialization-of-y
  (is (= true (deserialize bool-serializer "Y")))
  (is (= true (deserialize bool-serializer "y"))))

(defspec bool-deserialization-of-anything-not-y
  (prop/for-all [v gen/simple-type]
                (is (= false (deserialize bool-serializer v)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; date-serializer

(defspec date-deserialization-of-sql-dates
  (prop/for-all [d (gen/fmap (fn [[y m d]] (java.sql.Date. y m d))
                             (gen/tuple gen/s-pos-int
                                        (gen/elements (range 1 12))
                                        (gen/elements (range 1 28))))]
                (is (instance? org.joda.time.DateTime
                               (deserialize date-serializer d)))))

(defspec date-deserialization-of-sql-timestamps
  (prop/for-all [d (gen/fmap (fn [[y m d h n s]]
                               (java.sql.Timestamp. y m d h n s 0))
                             (gen/tuple gen/s-pos-int
                                        (gen/elements (range 1 12))
                                        (gen/elements (range 1 28))
                                        (gen/elements (range 0 23))
                                        (gen/elements (range 0 59))
                                        (gen/elements (range 0 59))))]
                (is (instance? org.joda.time.DateTime
                               (deserialize date-serializer d)))))
