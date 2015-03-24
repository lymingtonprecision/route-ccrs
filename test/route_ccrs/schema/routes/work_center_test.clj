(ns route-ccrs.schema.routes.work-center-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [clojure.test.check.properties :as prop]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.routes :refer [WorkCenter]]))

(defn is-valid [x]
  (is-valid-to-schema WorkCenter x))

(defn not-valid [x]
  (not-valid-to-schema WorkCenter x))

(def work-center-types
  #{:internal :external})

(def gen-valid-id
  (gen/such-that (complement empty?) (gen/resize 5 gen/string-alphanumeric)))

(def gen-invalid-id
  (gen/one-of
   [(gen/return "")
    (gen/such-that #(> (count %) 5) gen/string-alphanumeric)
    (gen/such-that (complement string?) gen/simple-type)]))

(def gen-work-center
  (gen/hash-map
   :id gen-valid-id
   :type (gen/elements work-center-types)
   :hours-per-day (gen/such-that
                   #(and (not (zero? %)) (pos? %))
                   (gen/one-of [gen'/double gen/pos-int]))
   :potential-ccr? gen/boolean))

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
    id gen-invalid-id]
   (not-valid (assoc wc :id id))))

(defspec invalid-types
  (prop/for-all
   [wc gen-work-center
    t (gen/such-that
       #(not (contains? work-center-types %))
       gen/keyword)]
   (not-valid (assoc wc :type t))))

(defspec non-numeric-hours-per-day
  (prop/for-all
   [wc gen-work-center
    n (gen/one-of
       [(gen/such-that (complement number?) gen/simple-type)
        (gen/such-that (complement zero?) gen/neg-int)])]
   (not-valid (assoc wc :hours-per-day n))))

(defspec non-bool-potential-ccr
  (prop/for-all
   [wc gen-work-center
    b (gen/such-that #(not= (class %) java.lang.Boolean)
                     gen/simple-type)]
   (not-valid (assoc wc :potential-ccr? b))))

(defspec work-center-needs-all-fields
  (prop/for-all
   [wc gen-work-center]
   (not-valid (dissoc wc (rand-nth (keys wc))))))
