(ns route-ccrs.schema.date-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [use-fixtures deftest is]])
            #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            #?@(:clj  [[clj-time.core :as t]
                       [clj-time.coerce :as tc]]
                :cljs [[cljs-time.core :as t]
                       [cljs-time.coerce :as tc]
                       [cljs-time.extend]])
            [schema.core :refer [check]]
            [route-ccrs.schema.dates :refer [Date]]))

(defspec non-date-types-arent-dates
  (prop/for-all [v gen/simple-type]
                (is (not (nil? (check Date v))))))

#?(:clj
   (deftest java-dates-are-dates
     (is (nil? (check Date (java.util.Date.)))))
   :cljs
   (deftest js-dates-are-dates
     (is (nil? (check Date (js/Date.))))))

(deftest joda-datetimes-are-dates
  (is (nil? (check Date (t/now)))))

(deftest joda-datemidnights-are-dates
  (is (nil? (check Date (t/today-at-midnight)))))

(deftest joda-localdates-are-dates
  (is (nil? (check Date (tc/to-local-date (t/today))))))

(deftest joda-localdatetimes-are-dates
  (is (nil? (check Date (tc/to-local-date-time (t/now))))))
