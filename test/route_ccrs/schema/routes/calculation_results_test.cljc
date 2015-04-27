(ns route-ccrs.schema.routes.calculation-results-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.routes :refer [RouteCalculationResults]]
            [route-ccrs.generators.routes.calculation-results
             :refer [gen-calc-results gen-invalid-calc-results]]))

(defspec valid-route-calculations
  (prop/for-all [r (gen-calc-results)]
                (is-valid-to-schema RouteCalculationResults r)))

(defspec invalid-route-calculations
  (prop/for-all [r gen-invalid-calc-results]
                (not-valid-to-schema RouteCalculationResults r)))
