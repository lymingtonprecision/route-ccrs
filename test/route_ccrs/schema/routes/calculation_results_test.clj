(ns route-ccrs.schema.routes.calculation-results-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [clojure.test.check.properties :as prop]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.routes.ccr-test :as ccr]
            [route-ccrs.schema.routes :refer [RouteCalculationResults]]))

(defn gen-calc-results
  ([] (gen-calc-results {}))
  ([{:keys [ccr best-end-date total-touch-time total-buffer]
     :or {ccr (gen/one-of [(gen/return nil) (ccr/gen-ccr)])
          best-end-date gen-date
          total-touch-time gen/pos-int
          total-buffer gen/pos-int}}]
   (gen/hash-map
     :ccr ccr
     :best-end-date best-end-date
     :total-touch-time total-touch-time
     :total-buffer total-buffer)))

(def gen-neg-int-or-non-number
  (gen/one-of
    [(gen/such-that #(< % 0) gen/neg-int)
     (gen/such-that (complement number?) gen/simple-type)]) )

(def gen-invalid-calc-results
  (gen/one-of
    [; invalid ccr
     (gen-calc-results {:ccr (gen/such-that (complement nil?) gen/simple-type)})
     ; munged ccr
     (gen/fmap
       (fn [[m k v]] (assoc m k v))
       (gen/tuple (gen-calc-results) gen/keyword gen/simple-type))
     ; invalid best end date
     (gen-calc-results
       {:best-end-date (gen/one-of [(gen/return nil) gen/simple-type])})
     ; invalid touch time
     (gen-calc-results {:total-touch-time gen-neg-int-or-non-number})
     ; invalid buffer
     (gen-calc-results {:total-buffer gen-neg-int-or-non-number})]))

(defspec valid-route-calculations
  (prop/for-all [r (gen-calc-results)]
                (is-valid-to-schema RouteCalculationResults r)))

(defspec invalid-route-calculations
  (prop/for-all [r gen-invalid-calc-results]
                (not-valid-to-schema RouteCalculationResults r)))
