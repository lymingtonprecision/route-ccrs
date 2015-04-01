(ns route-ccrs.routes.calculation
  (:require [schema.core :as s]
            [clj-time.core :as t]
            [route-ccrs.schema.dates :refer [Date]]
            [route-ccrs.schema.routes :as rs]
            [route-ccrs.best-end-dates.protocols :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pieces of the overall puzzle

(defn ^:private ceil [n] (-> n Math/ceil int))

(s/defn operation-buffer-days :- s/Num
  "Returns the number of days buffered production that operation `o`
  should be scheduled for (can be fractional.)

  If `o` is using an `:internal` work center the buffer is 50% of the
  touch time, external operations are unbuffered."
  [o :- rs/Operation]
  (let [f (if (= :internal (-> o :work-center :type)) 1.5 1.0)]
    (-> (:touch-time o)
        (* f)
        (/ 60.0)
        (/ (-> o :work-center :hours-per-day)))))

(s/defn calculate-route-totals
  "Returns a map containing the `:total-touch-time` and `:total-buffer`
  of the routing `r`."
  [r :- rs/Route]
  {:total-touch-time (ceil (reduce + 0 (map :touch-time (:operations r))))
   :total-buffer (reduce + 0 (map operation-buffer-days (:operations r)))})

(s/defn potential-ccrs :- [rs/CCR]
  "Returns a collection of `CCR` records: one per potential CCR present
  in routing `r`."
  [r :- rs/Route]
  (let [{tb :total-buffer} (calculate-route-totals r)]
    (->> (:operations r)
         (reduce
           (fn [r o]
             (let [ccr? (-> o :work-center :potential-ccr?)
                   wcid (-> o :work-center :id)
                   ob (operation-buffer-days o)
                   ccr (if ccr?
                         (-> (get-in r [:ccrs wcid]
                                     {:id wcid
                                      :operation (:id o)
                                      :total-touch-time 0
                                      :pre-ccr-buffer (:pre-op-buffer r)
                                      :post-ccr-buffer (:post-op-buffer r)})
                             (update-in [:total-touch-time]
                                        (comp ceil +)
                                        (:touch-time o))
                             (update-in [:post-ccr-buffer] - ob)))
                   r (if ccr?
                       (assoc-in r [:ccrs wcid] ccr)
                       r)]
               (-> r
                   (update-in [:pre-op-buffer] + ob)
                   (update-in [:post-op-buffer] - ob))))
           {:ccrs {} :pre-op-buffer 0 :post-op-buffer tb})
         :ccrs
         vals)))

(s/defn calculate-ccr-results :- [rs/RouteCalculationResults]
  "Returns a collection of `RouteCalculationResults` corresponding to
  the provided `ccrs` with end dates calculated based on a start date
  of `sd` (defaulting to today.)

  `edc` **must** implement the `ManufacturingEndDateCalculator`
  protocol."
  ([ccrs :- [rs/CCR], edc] (calculate-ccr-results ccrs edc (t/today)))
  ([ccrs :- [rs/CCR], edc, sd :- Date]
   {:pre [(satisfies? ManufacturingEndDateCalculator edc)]}
   (map
     (fn [ccr]
       {:ccr ccr
        :total-touch-time 0
        :total-buffer 0
        :best-end-date (work-center-end-date edc
                                             (:id ccr)
                                             (:total-touch-time ccr)
                                             (:pre-ccr-buffer ccr)
                                             (:post-ccr-buffer ccr)
                                             sd)})
     ccrs)))

(s/defn calculate-route-results :- rs/RouteCalculationResults
  "Returns a `RouteCalculationResults` map containing the results of
  calculating the best end date for routing `r` assuming no CCRs and
  starting production as of `sd` (defaulting to today.)

  `edc` **must** implement the `IntervalEndDateCalculator` protocol."
  ([r :- rs/Route, edc] (calculate-route-results r edc (t/today)))
  ([r :- rs/Route, edc, sd :- Date]
   {:pre [(satisfies? IntervalEndDateCalculator edc)]}
   (let [t (calculate-route-totals r)
         ed (interval-end-date edc (:total-buffer t) sd)]
     (merge t {:ccr nil :best-end-date ed}))))

(s/defn update-route-from-operations :- rs/CalculatedRoute
  "Returns a copy of the route `r` with freshly calculated calculation
  results (best end date, CCR, etc.) using `edc` to calculate the end
  date from the start date `sd` (defaulting to today) from the touch
  times and resulting buffers defined by the routes operations.

  `edc` **must** implement the `IntervalEndDateCalculator` and
  `ManufacturingEndDateCalculator` protocols."
  [r :- rs/Route, edc, sd :- Date]
  {:pre [(satisfies? IntervalEndDateCalculator edc)
         (satisfies? ManufacturingEndDateCalculator edc)]}
  (let [ccrs (potential-ccrs r)
        t (if (seq ccrs) (calculate-route-totals r) {})
        rcr (if (seq ccrs)
              (calculate-ccr-results ccrs edc sd)
              [(calculate-route-results r edc sd)])]
    (->> rcr
         (sort-by :best-end-date)
         last
         (#(merge r % t)))))

(s/defn update-route-from-existing-calculation :- rs/CalculatedRoute
  "Returns a copy of the route `r` with freshly calculated calculation
  results (best end date, CCR, etc.) using `edc` to calculate the end
  date from the start date `sd` (defaulting to today) from the currently
  defined CCR or route totals.

  `edc` **must** implement the `IntervalEndDateCalculator` and
  `ManufacturingEndDateCalculator` protocols."
  [r :- rs/CalculatedRoute, edc, sd :- Date]
  {:pre [(satisfies? IntervalEndDateCalculator edc)
         (satisfies? ManufacturingEndDateCalculator edc)]}
  (let [rcr (if-let [ccr (:ccr r)]
              (-> (calculate-ccr-results [ccr] edc sd) first)
              (-> {:best-end-date
                   (interval-end-date edc (:total-buffer r) sd)}))]
    (merge r (dissoc rcr :total-touch-time :total-buffer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn update-route-calculation :- rs/CalculatedRoute
  "Returns a copy of the route `r` with freshly calculated calculation
  results (best end date, CCR, etc.) using `edc` to calculate the end
  date from the start date `sd` (defaulting to today.)

  `edc` **must** implement the `IntervalEndDateCalculator` and
  `ManufacturingEndDateCalculator` protocols."
  ([r :- rs/Route, edc] (update-route-calculation r edc (t/today)))
  ([r :- rs/Route, edc, sd :- Date]
   {:pre [(satisfies? IntervalEndDateCalculator edc)
          (satisfies? ManufacturingEndDateCalculator edc)]}
   (cond
     (seq (:operations r))
     (update-route-from-operations r edc sd)
     (nil? (s/check rs/CalculatedRoute r))
     (update-route-from-existing-calculation r edc sd)
     :else nil)))
