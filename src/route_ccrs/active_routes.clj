(ns route-ccrs.active-routes
  (:require [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defquery]]))

(defquery active-routes "route_ccrs/sql/active_routes.sql")

(defn route-id [r]
  (select-keys r [:contract
                  :part_no
                  :bom_type_db
                  :routing_revision_no
                  :routing_alternative_no]))

(defn sorted-operations []
  (sorted-set-by
    (fn [x y]
      (compare (:operation_no x)
               (:operation_no y))) ))

(defn transduce-routes [step]
  (let [routes (volatile! {})]
    (fn
      ([] (step))
      ([r]
       (step (reduce step r @routes)))
      ([r o]
       (let [i (route-id o)
             route (conj (get @routes i (sorted-operations)) o)
             c (:operation_count o)]
         (if (and c (= (count route) c))
           (step r route)
           (do
             (vreset! routes (assoc @routes i route))
             r)))))))
