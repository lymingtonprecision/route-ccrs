(ns route-ccrs.schema.structures.purchased-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.ids.manufacturing-method-test :as mm]
            [route-ccrs.schema.purchased-raw-part-test :as raw]
            [route-ccrs.schema.parts :refer [PurchasedStructure]]))

(defn gen-valid
  ([] (gen-valid {}))
  ([{:keys [id components lead-time best-end-date]
     :or {id (gen/fmap #(assoc % :type :purchased) mm/gen-manufacturing-method)
          components (gen/return {})
          lead-time (gen-such-that pos? gen/pos-int)
          best-end-date gen-date}}]
   (gen/hash-map
     :id id
     :components components
     :lead-time lead-time
     :best-end-date best-end-date)))

(def gen-invalid
  (gen/one-of
    [; invalid method type
     (gen-valid {:id (gen-such-that #(not= (:type %) :purchased)
                                    mm/gen-manufacturing-method)})
     ; invalid id
     (gen-valid {:id mm/gen-invalid-manufacturing-method})
     ; nil components
     (gen/fmap #(assoc % :components nil) (gen-valid))
     ; invalid lead time
     (gen-valid {:lead-time
                 (gen/one-of
                   [(gen-such-that neg? gen/neg-int)
                    (gen-such-that (complement number?) gen/simple-type)])})
     ; invalid end date
     (gen-valid {:best-end-date gen/simple-type})
     ; missing field
     (gen/fmap #(dissoc % (rand-nth (keys %))) (gen-valid))
     ; invalid extra field
     (gen-with-extra-fields (gen-valid))]))

(defspec valid-empty-purchased-structure
  (prop/for-all [s (gen-valid)] (is-valid-to-schema PurchasedStructure s)))

(defspec valid-single-level-purchased-structure
  25
  (prop/for-all
    [s (gen-valid {:components (gen/map gen/simple-type (raw/gen-raw-part))})]
    (is-valid-to-schema PurchasedStructure s)))

(defspec invalid-empty-purchased-structure
  (prop/for-all [s gen-invalid] (not-valid-to-schema PurchasedStructure s)))
