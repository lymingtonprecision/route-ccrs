(ns route-ccrs.generators.route
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj [com.gfredericks.test.chuck.generators :as gen'])
            [route-ccrs.generators.util :refer [gen-such-that]]
            [route-ccrs.generators.manufacturing-method :as ids]
            [route-ccrs.generators.operation :as op]
            [route-ccrs.generators.routes.calculation-results :as calc]
            [route-ccrs.schema.routes :refer [RouteCalculationResults]]))

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
                       #(reduce (fn [r k] (assoc r k nil)) {} %)
                       #?(:clj  (gen'/subsequence (keys RouteCalculationResults))
                          :cljs (-> (keys RouteCalculationResults)
                                    gen/elements
                                    gen/vector
                                    gen/not-empty)))])}}]
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
