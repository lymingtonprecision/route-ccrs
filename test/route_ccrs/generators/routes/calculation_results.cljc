(ns route-ccrs.generators.routes.calculation-results
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util
             :refer [gen-such-that gen-date gen-double]]
            [route-ccrs.generators.ccr :as ccr]))

(defn gen-calc-results
  ([] (gen-calc-results {}))
  ([{:keys [ccr ccr-queue best-end-date total-touch-time total-buffer]
     :or {ccr (gen/one-of [(gen/return nil) (ccr/gen-ccr)])
          ccr-queue gen/pos-int
          best-end-date gen-date
          total-touch-time gen/pos-int
          total-buffer (gen/one-of
                         [gen/pos-int
                          (gen-such-that #(>= % 0) gen-double)])}}]
   (gen/hash-map
     :ccr ccr
     :ccr-queue ccr-queue
     :best-end-date best-end-date
     :total-touch-time total-touch-time
     :total-buffer total-buffer)))

(def gen-neg-int-or-non-number
  (gen/one-of
    [(gen-such-that neg? gen/neg-int)
     (gen-such-that (complement number?) gen/simple-type)]))

(def gen-neg-int-or-non-integer
  (gen/one-of
    [gen-neg-int-or-non-number
     (gen-such-that (complement zero?) gen-double)]))

(def gen-invalid-calc-results
  (gen/one-of
    [; invalid ccr
     (gen-calc-results
       {:ccr (gen-such-that (complement nil?) gen/simple-type)})
     ; munged ccr
     (gen/fmap
       (fn [[m k v]] (assoc m k v))
       (gen/tuple (gen-calc-results) gen/keyword gen/simple-type))
     ; invalid ccr queue
     (gen-calc-results {:ccr-queue gen-neg-int-or-non-integer})
     ; invalid best end date
     (gen-calc-results
       {:best-end-date (gen/one-of [(gen/return nil) gen/simple-type])})
     ; invalid touch time
     (gen-calc-results {:total-touch-time gen-neg-int-or-non-integer})
     ; invalid buffer
     (gen-calc-results {:total-buffer gen-neg-int-or-non-number})]))
