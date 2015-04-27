(ns route-ccrs.schema.ids.manufacturing-method-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.ids :as ids]
            [route-ccrs.generators.manufacturing-method
             :refer [gen-manufacturing-method
                     gen-invalid-manufacturing-method]]))

(defspec valid-manufacturing-methods
  (prop/for-all [mm gen-manufacturing-method]
                (is-valid-to-schema ids/ManufacturingMethod mm)))

(defspec invalid-manufacturing-methods
  (prop/for-all [mm gen-invalid-manufacturing-method]
                (not-valid-to-schema ids/ManufacturingMethod mm)))
