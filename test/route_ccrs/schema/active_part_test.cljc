(ns route-ccrs.schema.active-part-test
  (:require #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest]])
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.parts :refer [ActivePart]]))

(def part-no "100123456R01")

(deftest active-part-records
  (is-valid-to-schema ActivePart {:id part-no :low-level-code 0})
  (not-valid-to-schema ActivePart {:id part-no})
  (not-valid-to-schema ActivePart {:id part-no :low-level-code nil})
  (not-valid-to-schema ActivePart {:id part-no :low-level-code "1"})
  (not-valid-to-schema ActivePart {:low-level-code nil})
  (not-valid-to-schema ActivePart {:id "invalid" :low-level-code 1}))
