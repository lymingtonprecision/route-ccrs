(ns route-ccrs.active-routes
  (:require [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defquery]]))

(defquery active-routes "route_ccrs/sql/active_routes.sql")

(def route-id-keys
  [:contract
   :part_no
   :lowest_level
   :bom_type_db
   :routing_revision_no
   :routing_alternative_no])

(defn route-id [r]
  (select-keys r route-id-keys))

(defn sorted-operations []
  (sorted-set-by
    (fn [x y]
      (compare (:operation_no x)
               (:operation_no y)))))

(defn transduce-routes
  "Transduces routing operation records into tuples of `[route-id operations]`
  where the `route-id` is a map of the fields uniquely identifying the route
  and `operations` is a sorted set of the operations that comprise the route.

  Requires the operations to have an `:operation_count` value signifying
  the total number of operations on the route to which they belong, so
  that the routes can be checked for completeness."
  [step]
  (let [routes (volatile! {})]
    (fn
      ([] (step))
      ([r]
       (let [v (if (empty? @routes)
                 r
                 (reduce step r @routes))]
         (vreset! routes {})
         (step v)))
      ([r o]
       (let [i (route-id o)
             route (conj (get @routes i (sorted-operations)) o)
             c (:operation_count o)]
         (if (= (int (count route)) (int c))
           (do
             (vreset! routes (dissoc @routes i))
             (step r [i route]))
           (do
             (vreset! routes (assoc @routes i route))
             r)))))))
