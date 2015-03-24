(ns route-ccrs.schema.active-part-test
  (:require [clojure.test :refer :all]
            [schema.core :refer [check]]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.parts :refer [ActivePart]]))

(def part-no "100123456R01")

(deftest active-part-records
  (is-valid-to-schema ActivePart {:part-no part-no :low-level-code 0})
  (not-valid-to-schema ActivePart {:part-no part-no})
  (not-valid-to-schema ActivePart {:part-no part-no :low-level-code nil})
  (not-valid-to-schema ActivePart {:part-no part-no :low-level-code "1"})
  (not-valid-to-schema ActivePart {:low-level-code nil})
  (not-valid-to-schema ActivePart {:part-no "invalid" :low-level-code 1}))
