(ns route-ccrs.schema.routes.ccr-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [clojure.test.check.properties :as prop]
            [schema.core :refer [check]]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.routes.work-center-test :as wc]
            [route-ccrs.schema.routes.operation-test :as op]
            [route-ccrs.schema.routes :refer [CCR]]))

(def gen-valid-buffer
  (gen/one-of
    [gen/pos-int
     (gen-such-that #(>= % 0) gen'/double)]))

(def gen-invalid-buffer
  (gen/one-of
   [(gen-such-that neg? gen/neg-int)
    (gen-such-that (complement number?) gen/simple-type)]))

(def gen-invalid-touch-time
  (gen/one-of
    [gen'/double
     (gen-such-that neg? gen/neg-int)
     (gen-such-that (complement number?) gen/simple-type)]))

(defn gen-ccr
  ([] (gen-ccr {}))
  ([{:keys [id operation total-touch-time pre-ccr-buffer post-ccr-buffer]
     :or {id wc/gen-valid-id
          operation op/gen-valid-id
          total-touch-time gen/pos-int
          pre-ccr-buffer gen-valid-buffer
          post-ccr-buffer gen-valid-buffer}}]
   (gen/hash-map
    :id id
    :operation operation
    :total-touch-time total-touch-time
    :pre-ccr-buffer pre-ccr-buffer
    :post-ccr-buffer post-ccr-buffer)))

(def gen-invalid-ccr
  (gen/one-of
    [; invalid id
     (gen-ccr {:id wc/gen-invalid-id})
     ; invalid operation
     (gen-ccr {:operation op/gen-invalid-id})
     ; invalid touch time
     (gen-ccr {:total-touch-time gen-invalid-touch-time})
     ; invalid buffers
     (gen-ccr {:pre-ccr-buffer gen-invalid-buffer})
     (gen-ccr {:post-ccr-buffer gen-invalid-buffer})
     ; extra keys
     (gen-with-extra-fields (gen-ccr))
     ; not a record
     gen/simple-type]))

(defspec valid-ccrs
  (prop/for-all [c (gen-ccr)] (is-valid-to-schema CCR c)))

(defspec invalid-ccrs
  (prop/for-all [c gen-invalid-ccr]
                (not-valid-to-schema CCR c)))
