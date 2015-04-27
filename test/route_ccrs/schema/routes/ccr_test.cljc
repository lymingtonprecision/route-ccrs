(ns route-ccrs.schema.routes.ccr-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.routes :refer [CCR]]
            [route-ccrs.generators.ccr :refer [gen-ccr gen-invalid-ccr]]))

(defspec valid-ccrs
  (prop/for-all [c (gen-ccr)] (is-valid-to-schema CCR c)))

(defspec invalid-ccrs
  (prop/for-all [c gen-invalid-ccr] (not-valid-to-schema CCR c)))
