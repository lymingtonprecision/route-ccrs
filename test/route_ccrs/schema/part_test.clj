(ns route-ccrs.schema.part-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.structured-part-test
             :refer [gen-structured-part gen-invalid-structured-part]]
            [route-ccrs.schema.purchased-raw-part-test
             :refer [gen-raw-part gen-invalid-raw-part]]
            [route-ccrs.schema.parts :refer [Part]]))

(defspec structured-parts-are-parts
  (prop/for-all [p (gen-structured-part)] (is-valid-to-schema Part p)))

(defspec raw-parts-are-parts
  (prop/for-all [p (gen-raw-part)] (is-valid-to-schema Part p)))

(defspec invalid-parts
  (prop/for-all
    [p (gen/one-of [gen-invalid-structured-part gen-invalid-raw-part])]
    (not-valid-to-schema Part p)))

(defspec parts-must-be-the-correct-type
  (prop/for-all
    [p (gen/fmap
         #(assoc % :type (if (= (:type %) :raw) :structured :raw))
         (gen/one-of [(gen-structured-part) (gen-raw-part)]))]
    (not-valid-to-schema Part p)))

(defspec unknown-part-types-dont-validate
  (prop/for-all
    [p (gen/fmap
         (fn [[p t]] (assoc p :type t))
         (gen/tuple
           (gen/one-of [(gen-structured-part) (gen-raw-part)])
           (gen/one-of
             [gen/simple-type
              (gen-such-that
                #(not (contains? #{:structured :raw} %))
                gen/keyword)])))]
    (not-valid-to-schema Part p)))

(defspec parts-cant-have-fields-beyond-those-of-their-type
  (prop/for-all
    [p (gen-with-extra-fields
         (gen/one-of [(gen-structured-part) (gen-raw-part)])
         {:max 5})]
    (not-valid-to-schema Part p)))
