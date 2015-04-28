(ns route-ccrs.schema.routes-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest]])
            #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.generators.util :refer [gen-such-that]]
            [route-ccrs.generators.route
             :refer [gen-calculated-route
                     gen-uncalculated-route
                     gen-invalid-calculated-route
                     gen-invalid-uncalculated-route]]
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.routes
             :refer [CalculatedRoute UncalculatedRoute
                     Route RouteList RoutedItem]]))

(defspec valid-calculated-routes
  (prop/for-all [r (gen-calculated-route)]
                (is-valid-to-schema CalculatedRoute r)))

(defspec invalid-calculated-routes
  (prop/for-all [r gen-invalid-calculated-route]
                (not-valid-to-schema CalculatedRoute r)))

(defspec valid-uncalculated-routes
  (prop/for-all [r (gen-uncalculated-route)]
                (is-valid-to-schema UncalculatedRoute r)))

(defspec invalid-uncalculated-routes
  (prop/for-all [r gen-invalid-uncalculated-route]
                (not-valid-to-schema CalculatedRoute r)))

(defspec valid-routes
  (prop/for-all [r (gen/one-of [(gen-calculated-route)
                                (gen-uncalculated-route)])]
                (is-valid-to-schema Route r)))

(defspec invalid-routes
  (prop/for-all [r (gen/one-of [gen-invalid-calculated-route
                                gen-invalid-uncalculated-route])]
                (not-valid-to-schema Route r)))

(defspec valid-route-list
  10
  (prop/for-all [l (gen/map
                     gen/simple-type
                     (gen/one-of [(gen-calculated-route)
                                  (gen-uncalculated-route)]))]
                (is-valid-to-schema RouteList l)))

(deftest route-list-ids-must-not-be-nil
  (not-valid-to-schema RouteList
                       {nil (first (gen/sample (gen-calculated-route) 1))}))

(deftest all-route-list-values-must-be-routes
  (not-valid-to-schema RouteList
                       {1 (first (gen/sample (gen-calculated-route) 1))
                        2 (first (gen/sample gen/simple-type 1))}))

(defspec routed-items-must-have-route-in-use
  (prop/for-all
    [[k k2] (gen-such-that (fn [[k1 k2]] (not= k1 k2))
                           (gen/tuple gen/simple-type gen/simple-type))
     r (gen-calculated-route)]
    (do (not-valid-to-schema RoutedItem {:routes {k r}})
        (not-valid-to-schema RoutedItem {:routes {k r} :route-in-use nil})
        (not-valid-to-schema RoutedItem {:routes {k r} :route-in-use k2})
        (is-valid-to-schema RoutedItem {:routes {k r} :route-in-use k}))))
