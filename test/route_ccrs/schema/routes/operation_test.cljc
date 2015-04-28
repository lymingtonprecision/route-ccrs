(ns route-ccrs.schema.routes.operation-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.routes :refer [Operation]]
            [route-ccrs.generators.operation
             :refer [gen-operation gen-invalid-operation]]))

(defspec valid-operations
  (prop/for-all [o (gen-operation)] (is-valid-to-schema Operation o)))

(defspec invalid-operations
  (prop/for-all [o gen-invalid-operation]
                (not-valid-to-schema Operation o)))
