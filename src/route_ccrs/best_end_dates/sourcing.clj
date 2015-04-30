(ns route-ccrs.best-end-dates.sourcing
  "Exports a multi-method for retrieving the best end date for a sourced
  record."
  (:require [schema.core :as s]
            [clj-time.core :as t]
            [route-ccrs.schema.dates :refer [DateInst]]
            [route-ccrs.schema.parts :as ps]
            [route-ccrs.util :refer [defmethods]]
            [route-ccrs.best-end-dates.protocols :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private multi-method declarations

(defmulti ^:private -end-date-from-source
  (fn [r edc sd] (first (:source r))))

(defmethods -end-date-from-source [{[_ s] :source, :as r} c sd]
  :fixed-leadtime (interval-end-date c s sd)
  :fictitious (or sd (t/today))
  :stock (or sd (t/today))
  :shop-order (shop-order-end-date c s)
  :purchase-order (purchase-order-end-date c s))

(defmethod -end-date-from-source :default [r _ _]
  (if-let [s (:source r)]
    (throw (IllegalArgumentException.
            (str s " is not a valid end date source")))
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn end-date-from-source :- (s/maybe DateInst)
  "Returns the end date of the defined `:source` in `r`,
  using `edc` to calculate or resolve the end date as required.

  `edc` **must** implement the `EndDateResolver` and
  `IntervalEndDateCalculator` protocols.

  End dates will be resolved from the start date `sd` if provided.

  Returns `nil` if `r` has no source defined or the end date cannot be
  resolved and throws an `IllegalArgumentException` if the defined
  source is invalid."
  ([r :- ps/Sourced edc] (end-date-from-source r edc (t/today)))
  ([r :- ps/Sourced edc sd :- DateInst]
   {:pre [(satisfies? EndDateResolver edc)
          (satisfies? IntervalEndDateCalculator edc)]}
   (-end-date-from-source r edc sd)))
