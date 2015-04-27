(ns route-ccrs.schema.structures.purchased-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.generators.raw-part :as raw]
            [route-ccrs.generators.structures.purchased
             :refer [gen-purch-struct gen-invalid-purch-struct]]
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.parts :refer [PurchasedStructure]]))

(defspec valid-empty-purchased-structure
  (prop/for-all [s (gen-purch-struct)] (is-valid-to-schema PurchasedStructure s)))

(defspec valid-single-level-purchased-structure
  25
  (prop/for-all
    [s (gen-purch-struct {:components (gen/map gen/simple-type (raw/gen-raw-part))})]
    (is-valid-to-schema PurchasedStructure s)))

(defspec invalid-empty-purchased-structure
  (prop/for-all [s gen-invalid-purch-struct]
                (not-valid-to-schema PurchasedStructure s)))
