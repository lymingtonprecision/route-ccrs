(ns route-ccrs.schema.routes.operation-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [clojure.test.check.properties :as prop]
            [schema.core :refer [check]]
            [route-ccrs.generators.util :refer :all]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.routes.work-center-test :as wc]
            [route-ccrs.schema.routes :refer [Operation]]))

(def gen-valid-id (gen-such-that pos? gen/pos-int))
(def gen-invalid-id (gen/one-of
                     [gen/neg-int
                      gen'/double
                      (gen-such-that #(if (number? %) (< % 1) true)
                                     gen/simple-type)]))

(defn gen-operation
  ([] (gen-operation {}))
  ([{:keys [id touch-time work-center description]
     :or {id gen-valid-id
          description gen/string-ascii
          touch-time gen/pos-int
          work-center wc/gen-work-center}}]
   (gen/hash-map
    :id id
    :description description
    :touch-time touch-time
    :work-center work-center)))

(def gen-invalid-operation
  (gen/one-of
   [; invalid id
    (gen-operation {:id gen-invalid-id})
     ; invalid work center
    (gen-operation {:work-center gen/simple-type})
     ; invalid touch time
    (gen-operation {:touch-time
                    (gen/one-of
                     [(gen-such-that neg? gen/neg-int)
                      gen'/double
                      (gen-such-that (complement number?) gen/simple-type)])})
     ; not a record
    gen/simple-type]))

(defspec valid-operations
  (prop/for-all [o (gen-operation)] (is-valid-to-schema Operation o)))

(defspec invalid-operations
  (prop/for-all [o gen-invalid-operation]
                (not-valid-to-schema Operation o)))
