(ns route-ccrs.schema.routes-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [com.gfredericks.test.chuck.properties :as prop']
            [clojure.test.check.properties :as prop]
            [schema.core :refer [check]]
            [route-ccrs.generators.util :refer :all]
            [route-ccrs.generators.manufacturing-method :as ids]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.routes.work-center-test :as wc]
            [route-ccrs.schema.routes.operation-test :as op]
            [route-ccrs.schema.routes.calculation-results-test :as calc]
            [route-ccrs.schema.routes :refer :all]))

(defn gen-valid-id
  ([]
   (gen-such-that
     #(not= :purchased (:type %))
     ids/gen-manufacturing-method))
  ([t]
   (gen/fmap
     #(assoc % :type t)
     ids/gen-manufacturing-method)))

(defn gen-calculated-route
  ([] (gen-calculated-route {}))
  ([{:keys [id operations results description]
     :or {id (gen-valid-id)
          description gen/string-ascii
          operations (gen/one-of
                       [(gen/return {})
                        (gen/hash-map
                          :operations (gen/one-of
                                        [(gen/return nil)
                                         (gen/vector (op/gen-operation))]))])
          results (calc/gen-calc-results)}}]
   (gen/fmap
     (partial apply merge)
     (gen/tuple
       (gen/hash-map :id id :description description)
       operations
       results))))

(def gen-invalid-calculated-route
  (gen/one-of
    [; invalid id
     (gen-calculated-route {:id ids/gen-invalid-manufacturing-method})
     ; invalid operations
     (gen-calculated-route {:operations
                            (gen/hash-map
                              :operations
                              (gen/one-of
                                [(gen-such-that
                                   (complement nil?)
                                   gen/simple-type)
                                 (gen/return {})
                                 (gen-such-that
                                   not-empty
                                   (gen/vector gen/simple-type))]))})
     ; incomplete results
     (gen/fmap
       #(dissoc % (rand-nth (keys RouteCalculationResults)))
       (gen-calculated-route))
     ; invalid results
     (gen-calculated-route {:results calc/gen-invalid-calc-results})]))

(defn gen-uncalculated-route
  ([] (gen-uncalculated-route {}))
  ([{:keys [id operations results]
     :or {id (gen-valid-id)
          operations (gen/not-empty (gen/vector (op/gen-operation)))
          results (gen/one-of
                    [(gen/return {})
                     (gen/fmap
                       #(reduce(fn [r k] (assoc r k nil)) {} %)
                       (gen'/subsequence (keys RouteCalculationResults)))])}}]
   (gen/fmap
     (partial apply merge)
     (gen/tuple
       (gen/hash-map :id id :operations operations)
       results))))

(def gen-invalid-uncalculated-route
  (gen/one-of
    [; invalid id
     (gen-uncalculated-route {:id ids/gen-invalid-manufacturing-method})
     ; invalid operations
     (gen-uncalculated-route {:operations (gen/vector gen/simple-type)})
     ; missing operations
     (gen/fmap #(dissoc % :operations) (gen-uncalculated-route))
     ; non-nil result fields
     (gen/fmap
       (fn [[m k v]] (assoc m k v))
       (gen/tuple
         (gen-uncalculated-route)
         (gen/elements (keys RouteCalculationResults))
         (gen-such-that (complement nil?) gen/simple-type)))]))

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
  (prop'/for-all
    [k gen/simple-type
     k2 (gen-such-that #(not= % k) gen/simple-type)
     r (gen-calculated-route)]
    (do (not-valid-to-schema RoutedItem {:routes {k r}})
        (not-valid-to-schema RoutedItem {:routes {k r} :route-in-use nil})
        (not-valid-to-schema RoutedItem {:routes {k r} :route-in-use k2})
        (is-valid-to-schema RoutedItem {:routes {k r} :route-in-use k}))))
