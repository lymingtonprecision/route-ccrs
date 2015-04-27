(ns route-ccrs.schema.structured-part-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.generators.structured-part
             :refer [gen-structured-part
                     gen-invalid-structured-part
                     gen-multilevel]]
            [route-ccrs.schema.parts :refer [StructuredPart]]))

(def ^:dynamic *num-multilevel-tests* 10)

(defspec valid-structured-parts
  (prop/for-all [p (gen-structured-part)] (is-valid-to-schema StructuredPart p)))

(defspec invalid-structured-parts
  (prop/for-all
    [p gen-invalid-structured-part]
    (not-valid-to-schema StructuredPart p)))

(defspec must-have-structs
  (prop/for-all
    [p (gen-structured-part {:structs (gen/return {})})]
    (not-valid-to-schema StructuredPart p)))

(defspec valid-multilevel
  *num-multilevel-tests*
  (prop/for-all
    [p (gen-multilevel)]
    (is-valid-to-schema StructuredPart p)))

(defspec invalid-multilevel
  *num-multilevel-tests*
  (prop/for-all
    [p (gen-multilevel false)]
    (not-valid-to-schema StructuredPart p)))
