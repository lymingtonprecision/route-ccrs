(ns route-ccrs.schema.part-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.generators.util
             :refer [gen-such-that gen-with-extra-fields]]
            [route-ccrs.generators.raw-part
             :refer [gen-raw-part gen-invalid-raw-part]]
            [route-ccrs.generators.structured-part
             :refer [gen-structured-part gen-invalid-structured-part]]
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
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
