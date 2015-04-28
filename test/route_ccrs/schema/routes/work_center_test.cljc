(ns route-ccrs.schema.routes.work-center-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.generators.util
             :refer [gen-such-that gen-with-extra-fields]]
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.routes :refer [WorkCenter]]
            [route-ccrs.generators.work-center :as wc :refer [gen-work-center]]))

(defn is-valid [x]
  (is-valid-to-schema WorkCenter x))

(defn not-valid [x]
  (not-valid-to-schema WorkCenter x))

(defspec valid-work-centers
  (prop/for-all [wc gen-work-center] (is-valid wc)))

(defspec disallows-extra-fields
  10
  (prop/for-all
   [wc (gen-with-extra-fields gen-work-center)]
   (not-valid wc)))

(defspec invalid-id
  (prop/for-all
   [wc gen-work-center
    id wc/gen-invalid-id]
   (not-valid (assoc wc :id id))))

(defspec invalid-types
  (prop/for-all
   [wc gen-work-center
    t (gen-such-that #(not (contains? wc/work-center-types %)) gen/keyword)]
   (not-valid (assoc wc :type t))))

(defspec non-numeric-hours-per-day
  (prop/for-all
   [wc gen-work-center
    n (gen/one-of
       [(gen-such-that (complement number?) gen/simple-type)
        (gen-such-that neg? gen/neg-int)])]
   (not-valid (assoc wc :hours-per-day n))))

(defspec non-bool-potential-ccr
  (prop/for-all
   [wc gen-work-center
    b (gen-such-that
        #(and (not (identical? % true)) (not (identical? % false)))
        gen/simple-type)]
   (not-valid (assoc wc :potential-ccr? b))))

(def work-center-fields
  (keys (first (gen/sample gen-work-center))))

(defspec work-center-needs-all-field-except-description
  (prop/for-all
   [wc gen-work-center
    k (gen/elements (remove #(= :description %) work-center-fields))]
   (not-valid (dissoc wc k))))
