(ns route-ccrs.schema.date-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.core :refer [check]]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.schema.dates :refer [Date]]))

(defspec non-date-types-arent-dates
  (prop/for-all [v gen/simple-type]
                (is (not (nil? (check Date v))))))

(deftest java-dates-are-dates
  (is (nil? (check Date (java.util.Date.)))))

(deftest joda-datetimes-are-dates
  (is (nil? (check Date (t/now)))))

(deftest joda-datemidnights-are-dates
  (is (nil? (check Date (t/today-at-midnight)))))

(deftest joda-localdates-are-dates
  (is (nil? (check Date (tc/to-local-date (t/today))))))

(deftest joda-localdatetimes-are-dates
  (is (nil? (check Date (tc/to-local-date-time (t/now))))))
