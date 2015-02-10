(ns route-ccrs.part
  (:require [yesql.core :refer [defquery]]))

(defquery active-parts "route_ccrs/sql/active_parts.sql")

(defn- transduce-by-shared-id
  [id-fn set-fn count-key step]
  (let [sets (volatile! {})]
    (fn
      ([] (step))
      ([r]
       (let [v (if (empty? @sets) r (reduce step r @sets))]
         (vreset! sets {})
         (step v)))
      ([r e]
       (let [i (id-fn e)
             s (conj (get @sets i (set-fn)) (dissoc e count-key))]
         (if (= (int (count s)) (int (get e count-key)))
           (do
             (vreset! sets (dissoc @sets i))
             (step r [i s]))
           (do
             (vreset! sets (assoc @sets i s))
             r)))))))

(defquery operations-for-part "route_ccrs/sql/buildable_routings_for_part.sql")

(def route-id-keys
  [:contract
   :part_no
   :bom_type_db
   :routing_revision_no
   :routing_alternative_no])

(defn route-id [r]
  (select-keys r route-id-keys))

(defn sorted-operation-set []
  (sorted-set-by
    (fn [x y]
      (compare (:operation_no x) (:operation_no y)))))

(defn transduce-routes
  "Transduces routing operation records into tuples of `[route-id operations]`
  where the `route-id` is a map of the fields uniquely identifying the route
  and `operations` is a sorted set of the operations that comprise the route.

  Requires the operations to have an `:operation_count` value signifying
  the total number of operations on the route to which they belong. Otherwise
  no routings will be returned until the transducer is asked to complete and
  flush its state."
  [step]
  (transduce-by-shared-id route-id sorted-operation-set :operation_count step))
