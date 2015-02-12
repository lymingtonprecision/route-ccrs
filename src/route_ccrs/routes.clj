(ns route-ccrs.routes
  (:require [yesql.core :refer [defquery]]
            [route-ccrs.processing :refer :all]
            [route-ccrs.ccr :as ccr]))

(defquery operations-for-part "route_ccrs/sql/buildable_routings_for_part.sql")

(def route-id-keys
  [:contract
   :part_no
   :bom_type_db
   :routing_revision_no
   :routing_alternative_no])

(defn route-id [r]
  (select-keys r route-id-keys))

(defn sorted-operation-set
  "Returns an empty set that will sort its elements by their `:operation_no`"
  []
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

(defn process-route
  "Compares the current CCR for a route, as returned from the database, to
  that calculated for the given collection of operations and returns a tuple
  of `[route-ccr database-update]`

  The returned `route-ccr` is a map containing the `route-id` fields, the
  new CCR information for the route, and the routes best end date.

  The `database-update` is a variant of `[update-type update-fields]` where
  the `update-type` will be one of: `:insert`, `:update`, or `:replace` and
  `update-fields` is a map of the fields and their values that are required
  to perform the update."
  [db route-id operations]
  (let [c {:connection db}]
    (ccr/ccr-entry-updates
      route-id
      (ccr/get-last-known-route-ccr route-id c)
      (ccr/select-current-ccr operations c))))

(defn process-routes
  "Processes a collection of routes, passing each to `process-route`
  and returning a collection of the results."
  [db routes]
  (parallel-process #(apply process-route db %) routes))

(defquery insert-history-entry! "route_ccrs/sql/insert_history.sql")
(defquery insert-current-ccr! "route_ccrs/sql/insert_ccr.sql")
(defquery update-current-ccr! "route_ccrs/sql/update_ccr.sql")
(defquery replace-current-ccr! "route_ccrs/sql/replace_ccr.sql")

(defn update! [db [t r]]
  (let [c {:connection db}]
    (insert-history-entry! r c)
    (cond
      (= :replace t) (replace-current-ccr! r c)
      (= :update t) (update-current-ccr! r c)
      (= :insert t) (insert-current-ccr! r c))))
