(ns route-ccrs.schema.routes
  (:require [schema.core :as s]
            [route-ccrs.schema.dates :as d]
            [route-ccrs.schema.generic :as g]
            [route-ccrs.schema.ids :refer [ManufacturedMethodId]]))

(def WorkCenterId
  "A work center ID is a non-empty string of up to five characters."
  (s/both
   s/Str
   (s/pred (complement empty?) 'empty-id)
   (s/pred #(<= (count %) 5) 'id-over-5-chars)))

(def WorkCenter
  "A work center has *only* the following fields:

  `:id` a valid `WorkCenterId`
  `:type` either `:internal` or `:external`
  `:hours-per-day` a number greater than zero (integer or decimal/float)
  `:potential-ccr?` a boolean
  `:description` an optional string describing the work center"
  {:id WorkCenterId
   (s/optional-key :description) (s/maybe s/Str)
   :type (s/enum :internal :external)
   :hours-per-day (s/both s/Num (s/pred #(> % 0) 'non-zero-number))
   :potential-ccr? s/Bool})

(def OperationId
  "An operation ID is a non-zero positive integer."
  g/int-gt-zero)

(def ^:private touch-time g/int-gte-zero)
(def ^:private buffer g/num-gte-zero)

(def Operation
  "An operation has *only* the following fields:

  * `:id` a valid `OperationId`
  * `:touch-time` zero, or a positive integer; the number of minutes run
    time of the operation
  * `:work-center` a valid `WorkCenter` record
  * `:description` an optional string describing the operation"
  {:id OperationId
   (s/optional-key :description) (s/maybe s/Str)
   :touch-time touch-time
   :work-center WorkCenter})

(def CCR
  "A CCR record has *only* the following fields:

  * `:id` a valid `WorkCenterId`
  * `:operation` a valid `OperationId`
  * `:total-touch-time` zero, or a positive integer; the total run time,
    in minutes, of operations against the CCR
  * `:pre-ccr-buffer` zero, or a positive number; the total number of
    days production before the CCR
  * `:post-ccr-buffer` zero, or a positive number; the total number of
    days production after the CCR"
  {:id WorkCenterId
   :operation OperationId
   :total-touch-time touch-time
   :pre-ccr-buffer buffer
   :post-ccr-buffer buffer})

(def RouteCalculationResults
  "Calculations of the CCR/Best End Date for a route result in a map
  of *only* the following fields:

  * `:ccr` nil or a valid `CCR` record
  * `:best-end-date` a valid `DateInst`
  * `:total-touch-time` zero, or a positive integer; the total run time
    of the route in minutes
  * `:total-buffer` zero, or a positive number; the total buffered run
    time of the route in working days"
  {:ccr (s/maybe CCR)
   :best-end-date d/DateInst
   :total-touch-time touch-time
   :total-buffer buffer})

(def UncalculatedRoute
  "An uncalculated route consists of a valid `ManufacturedMethodId`,
  `:id`, a non-empty list of `Operation`s, `:operations`, and an optional
  `:description`.

  It _may_ also contain all, some, or none of the `RouteCalculationResults`
  fields set to `nil`. None of these fields should be present with a non-nil
  value."
  (merge
   {:id ManufacturedMethodId
    (s/optional-key :description) (s/maybe s/Str)
    :operations (s/both [Operation] (s/pred not-empty 'not-empty))}
   (reduce
    (fn [r [k _]]
      (assoc r (s/optional-key k) (s/eq nil)))
    {}
    RouteCalculationResults)))

(def CalculatedRoute
  "A calculated route consists of a valid `ManufacturedMethodId`,
  `:id`, an optional list of `Operation`s, `:operations`, an optional
  `:description`, and a valid set of `RouteCalculationResults` fields.

  The `:operations` may be omitted entirely, an empty collection, `nil`,
  or a collection of `Operation` records."
  (merge
   {:id ManufacturedMethodId
    (s/optional-key :description) (s/maybe s/Str)
    (s/optional-key :operations) [Operation]}
   RouteCalculationResults))

(def Route
  "A route is either calculated or un-calculated. If it contains any of
  the `RouteCalculationResults` fields with a non-nil value then it
  must correspond to the `CalculatedRoute` schema. Otherwise it must
  match the `UncalculatedRoute` schema."
  (s/conditional
   #(some (partial get %) (keys RouteCalculationResults)) CalculatedRoute
   :else UncalculatedRoute))

(def RouteList
  "A route list is a map where the keys are any non-nil value and the
  values are valid `Route` records."
  {g/any-non-nil Route})

(def valid-route-in-use?
  "Predicate to verify that a `RoutedItem`s `:route-in-use` corresponds
  to an entry in its `:routes` list."
  (s/pred #(contains? (:routes %) (:route-in-use %)) 'valid-route-in-use))

(defn map->Routed
  "Takes a schema _map_, `sm`, and returns a new schema that is a copy
  of it with the `RoutedItem` fields and `valid-route-in-use?` predicate
  added.

  Any additional arguments are assumed to be further schemas which
  the resulting schema should subsume."
  [sm & s]
  (apply
    s/both
    (merge sm {:routes RouteList :route-in-use g/any-non-nil})
    valid-route-in-use?
    s))

(def RoutedItem
  "A routed item is a record that contains the fields:

  `:routes` a valid `RouteList`
  `:route-in-use` a value identifying an entry in `:routes`

  The value of `:route-in-use` *cannot* be `nil` and *must* be a valid
  key within the `:routes` collection."
  (map->Routed {s/Any s/Any}))
