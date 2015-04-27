(ns route-ccrs.schema.date-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest]])
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
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.dates :as ds]))

(defspec non-date-types-arent-dates
  (prop/for-all [v gen/simple-type] (not-valid-to-schema ds/DateInst v)))

#?(:clj
   (deftest java-dates-are-dates
     (is-valid-to-schema ds/DateInst (java.util.Date.)))
   :cljs
   (deftest js-dates-are-dates
     (is-valid-to-schema ds/DateInst (js/Date.))))

(deftest joda-datetimes-are-dates
  (is-valid-to-schema ds/DateInst (t/now)))

(deftest joda-datemidnights-are-dates
  (is-valid-to-schema ds/DateInst (t/today-at-midnight)))

(deftest joda-localdates-are-dates
  (is-valid-to-schema ds/DateInst (tc/to-local-date (t/today))))

(deftest joda-localdatetimes-are-dates
  (is-valid-to-schema ds/DateInst (tc/to-local-date-time (t/now))))
