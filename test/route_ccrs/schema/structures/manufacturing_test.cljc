(ns route-ccrs.schema.structures.manufacturing-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.generators.util :refer [gen-such-that]]
            [route-ccrs.generators.raw-part :as raw]
            [route-ccrs.generators.structures.manufactured
             :refer [gen-valid gen-invalid]]
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.parts
             :refer [route-types-match-structure? ManufacturedStructure]]))

(def gen-route-map
  (gen/fmap
    (fn [r]
      {:id {:type (-> r vals first :id :type)}
       :routes r})
    (gen/bind
      gen/simple-type
      (fn [t] (gen/map gen/simple-type (gen/return {:id {:type t}}))))))

(defspec route-types-match-structure-is-route-key-agnostic
  25
  (prop/for-all
    [s gen-route-map]
    (is-valid-to-schema route-types-match-structure? s)))

(defspec route-types-match-structure-fails-on-single-mismatch
  25
  (prop/for-all
    [s (gen/fmap
         (fn [[s t]]
           (assoc-in s [:routes (rand-nth (keys (:routes s))) :id :type] t))
         (gen-such-that
           (fn [[s y]] (not= (-> s :id :type) y))
           (gen/tuple gen-route-map gen/simple-type)))]
    (not-valid-to-schema route-types-match-structure? s)))

(defspec valid-single-level-manufactured-structure
  (prop/for-all [s (gen-valid)]
                (is-valid-to-schema ManufacturedStructure s)))

(defspec invalid-single-level-manufactured-structure
  (prop/for-all [s gen-invalid]
                (not-valid-to-schema ManufacturedStructure s)))

(defspec invalid-component-list-invalidates-structure
  5
  (prop/for-all
    [s (gen-valid
         {:components
          (gen/not-empty
            (gen/map
              gen/simple-type
              (gen/one-of [raw/gen-invalid-raw-part gen/simple-type])))})]
    (not-valid-to-schema ManufacturedStructure s)))
