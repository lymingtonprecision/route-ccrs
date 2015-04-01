(ns route-ccrs.best-end-dates.protocols)

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
    `post-wc-days` days worth of processing elsewhere."))
