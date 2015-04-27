(ns route-ccrs.schema.purchased-raw-part-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.generators.util :refer :all]
            [route-ccrs.generators.raw-part :refer :all]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.parts :refer [PurchasedRawPart]]))

(defspec valid-raw-parts
  (prop/for-all [p (gen-raw-part)] (is-valid-to-schema PurchasedRawPart p)))

(defspec invalid-raw-parts
  (prop/for-all [p gen-invalid-raw-part]
                (not-valid-to-schema PurchasedRawPart p)))
