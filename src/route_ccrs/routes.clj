(ns route-ccrs.routes
  (:require [yesql.core :refer [defquery]]
            [route-ccrs.processing :refer :all]
            [route-ccrs.route-ccr :as ccr]))

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

(defn deferred-routes [db part]
  (promise-transduced-query
    db
    (partial operations-for-part part)
    transduce-routes
    {}))

(defn process-route [db [route-id operations]]
  (let [c {:connection db}]
    (ccr/ccr-entry-updates
      route-id
      (ccr/get-last-known-ccr route-id c)
      (ccr/select-current-ccr operations c))))

(defn process-routes [db routes]
  (parallel-process (partial process-route db) routes))
