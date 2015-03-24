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

(defn gen-ccr
  ([] (gen-ccr {}))
  ([{:keys [id operation total-touch-time pre-ccr-buffer post-ccr-buffer]
     :or {id wc/gen-valid-id
          operation op/gen-valid-id
          total-touch-time (gen/such-that
                            #(>= % 0) (gen/one-of [gen'/double gen/pos-int]))
          pre-ccr-buffer gen/pos-int
          post-ccr-buffer gen/pos-int}}]
   (gen/hash-map
    :id id
    :operation operation
    :total-touch-time total-touch-time
    :pre-ccr-buffer pre-ccr-buffer
    :post-ccr-buffer post-ccr-buffer)))

(def gen-invalid-buffer
  (gen/one-of
   [(gen/such-that (complement zero?) (gen/one-of [gen'/double gen/neg-int]))
    (gen/such-that (complement integer?) gen/simple-type)]))

(def gen-invalid-ccr
  (gen/one-of
   [; invalid id
    (gen-ccr {:id wc/gen-invalid-id})
     ; invalid operation
    (gen-ccr {:operation op/gen-invalid-id})
     ; invalid touch time
    (gen-ccr {:total-touch-time
              (gen/such-that
               #(< % 0) (gen/one-of [gen'/double gen/neg-int]))})
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
