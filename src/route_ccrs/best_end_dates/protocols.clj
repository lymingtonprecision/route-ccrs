(ns route-ccrs.best-end-dates.protocols
  (:require [schema.core :as s]
            [route-ccrs.schema.dates :as d]
            [route-ccrs.schema.generic :as g]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

(s/defschema WorkCenterLoadResult
  "The result of a `ManufacturingEndDateCalculator`
  `work-center-end-date` calculation. Contains *only* the fields:

  * `:end-date` the calculated end date.
    * `:queue` the number of days queue at the work center (from
      `start-date + pre-wc-days`) before it has free capacity for work
      of the duration `time-at-wc`.
    * `:load-date` the date the work can be loaded to the work center
      (equivalent to `start-date + pre-wc-days + queue`.)"
  {:end-date d/DateInst
   :queue g/int-gte-zero
   :load-date d/DateInst})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defprotocol EndDateResolver
  "Returns the end dates of various types of record given their IDs."
  (shop-order-end-date [this order-id])
  (purchase-order-end-date [this order-id]))

(defprotocol IntervalEndDateCalculator
  (interval-end-date
    [this days]
    [this days start-date]
    "Returns the first working day `days` after `start-date` (defaulting
    to today.)"))

(defprotocol ManufacturingEndDateCalculator
  (work-center-end-date
    [this wc time-at-wc pre-wc-days post-wc-days]
    [this wc time-at-wc pre-wc-days post-wc-days start-date]
    "Calculates the end date on the specified work center of a job
    that starts `pre-wc-days` from `start-date` (defaulting to today),
    takes `time-at-wc` **minutes** at `wc`, and then has a further
    `post-wc-days` days worth of processing elsewhere.

    Returns a `WorkCenterLoadResult` or `nil` if the work center does
    not exist or any other parameter is invalid."))
