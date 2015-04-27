(ns route-ccrs.schema.purchased-raw-part-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.generators.raw-part
             :refer [gen-raw-part gen-invalid-raw-part]]
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.parts :refer [PurchasedRawPart]]))

(defspec valid-raw-parts
  (prop/for-all [p (gen-raw-part)] (is-valid-to-schema PurchasedRawPart p)))

(defspec invalid-raw-parts
  (prop/for-all [p gen-invalid-raw-part]
                (not-valid-to-schema PurchasedRawPart p)))
