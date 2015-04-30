(ns route-ccrs.best-end-dates.update
  "Provides methods for updating end dates within part, structure, and
  routing records:

  * `remove-best-end-dates` returns a copy of a part structure with
    all of the best end dates, at every level, removed. Useful for
    starting from a blank slate.
  * `update-best-end-date` returns a copy of a part, structure, or
    routing with an updated best end date.
  * `update-all-best-end-dates-under-part` returns a copy of a part
    with the best end date of itself and every child record (structures,
    routings, components) at every level updated."
  (:require [schema.core :as s]
            [clj-time.core :as t]
            [route-ccrs.schema.dates :refer [DateInst]]
            [route-ccrs.schema.parts :as ps]
            [route-ccrs.schema.routes :as rs]
            [route-ccrs.util :refer [defmethods sourced?]]
            [route-ccrs.util.schema-dispatch :refer [get-schema]]
            [clojure.zip :as zip]
            [route-ccrs.part-zipper :as pz :refer [part-zipper]]
            [route-ccrs.best-end-dates.protocols :refer :all]
            [route-ccrs.best-end-dates.sourcing :refer :all]
            [route-ccrs.routes.calculation :refer [update-route-calculation]]
            [route-ccrs.start-dates :refer [start-date]]))

(declare ^:private -update-best-end-date)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private node update methods

(defn remove-best-end-date
  "Given a part-zipper location returns the same location modified so
  that its value doesn't contain a best end date."
  [n]
  (if (nil? (s/check rs/CalculatedRoute (pz/node-val n)))
    (pz/edit-val n #(apply dissoc % (keys rs/RouteCalculationResults)))
    (pz/edit-val n assoc :best-end-date nil)))

(defn update-purchased-end-date
  "Returns `r` updated with a new end date based on its lead time,
  the start date `sd`, and the `IntervalEndDateCalculator` `edc`."
  [r edc sd]
  (assoc r :best-end-date (interval-end-date edc (:lead-time r) sd)))

(defn update-child-end-date
  "Returns a fn that takes a record `r`, end date calculator `edc`
  (which implements one or more of the calculation protocols in this
  namespace), and a start date `sd` and returns a copy of the record
  with the end date of the child item from the collection under key
  `mk` (using the value under key `ik` as its index) updated."
  [mk ik]
  (fn [r edc sd]
    (let [i (get r ik)]
      (assoc-in r [mk i] (-update-best-end-date (get (get r mk) i) edc sd)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private multi-method declarations

(defmulti ^:private -update-best-end-date
  (fn [r edc sd]
    (if (sourced? r)
      :source
      (get-schema -update-best-end-date r))))

(defmethod -update-best-end-date :source [r edc sd]
  (assoc r :best-end-date (end-date-from-source r edc sd)))

(.addMethod -update-best-end-date
            rs/Route
            update-route-calculation)

(.addMethod -update-best-end-date
            ps/PurchasedRawPart
            update-purchased-end-date)

(.addMethod -update-best-end-date
            ps/PurchasedStructure
            update-purchased-end-date)

(.addMethod -update-best-end-date
            ps/ManufacturedStructure
            (update-child-end-date :routes :route-in-use))

(.addMethod -update-best-end-date
            ps/StructuredPart
            (update-child-end-date :structs :struct-in-use))

(defmethod -update-best-end-date :default [r _ _]
  (throw (IllegalArgumentException.
          (str "no schema matches " r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn remove-best-end-dates :- ps/Part
  "Returns a copy of `part` with all current `best-end-dates` values
  removed (or set to `nil`, as appropriate.)"
  [part :- ps/Part]
  (loop [loc (part-zipper part)]
    (if (zip/end? loc)
      (pz/root-part loc)
      (let [n (if (:best-end-date (pz/node-val loc))
                (remove-best-end-date loc)
                loc)]
        (recur (zip/next n))))))

(defn update-best-end-date
  "Returns a copy of the record `r` with an updated best end date, using
  `edc` to resolve and calculate the new end dates and calculating
  new dates forwards from the `start-date` of the record (or `sd` if
  provided.)

  `edc` **must** implement the `EndDateResolver`,
  `IntervalEndDateCalculator`, and `ManufacturingEndDateCalculator`
  protocols.

  If `r` has a `:source` override then the end-date appropriate to that
  source will be used.

  If `r` is a purchased item (a raw part or purchased structure) then a
  new end date will be calculated from its lead time.

  If `r` is a routing then it will be updated with a new end date, CCR,
  and the other route calculation results.

  If `r` is a, non-sourced, manufacturing structure then the end date
  of its currently preferred routing will be updated.

  If `r` is a, non-sourced, structured part then the end date of its
  currently preferred structure will be updated (which may, in turn,
  defer to updating its currently preferred routing.)

  Throws an `IllegalArgumentException` if `r` does not match a schema
  for which we can update the end date."
  ([r edc] (update-best-end-date r edc (start-date r)))
  ([r edc sd]
   {:pre [(satisfies? EndDateResolver edc)
          (satisfies? IntervalEndDateCalculator edc)
          (satisfies? ManufacturingEndDateCalculator edc)]}
   (-update-best-end-date r edc sd)))

(s/defn update-all-best-end-dates-under-part :- ps/Part
  "Returns a copy of the part `p` with newly calculated best end dates
  for the part itself and every component record of which it is
  comprised.

  The calculation is performed from the \"bottom up\" so that each
  newly calculated end date feeds into the calculation of the next level
  up. All records at the lowest level, raw parts for example, will use
  `sd` as the start date from which their end date is calculated (which
  defaults to today.)

  `edc` **must** implement the `EndDateResolver`,
  `IntervalEndDateCalculator`, and `ManufacturingEndDateCalculator`
  protocols."
  ([p :- ps/Part, edc]
   (update-all-best-end-dates-under-part p edc (t/today)))
  ([p :- ps/Part, edc, sd :- DateInst]
   {:pre [(satisfies? EndDateResolver edc)
          (satisfies? IntervalEndDateCalculator edc)
          (satisfies? ManufacturingEndDateCalculator edc)]}
   (let [bottom (loop [loc (part-zipper p)]
                  (let [x (zip/next loc)]
                    (if (zip/end? x)
                      loc
                      (recur x))))
         phantom-node? #(or (nil? (zip/node %))
                            (contains? #{:routes :components} (pz/node-key %)))
         node-sd #(if (nil? (s/check rs/Route (pz/node-val %)))
                    (start-date (-> % zip/up zip/up pz/node-val) sd)
                    (start-date (pz/node-val %) sd))]
     (loop [loc bottom]
       (let [loc (if (not (phantom-node? loc))
                   (pz/edit-val loc update-best-end-date edc (node-sd loc))
                   loc)
             pn (zip/prev loc)]
         (if (nil? pn)
           (pz/root-part loc)
           (recur pn)))))))
