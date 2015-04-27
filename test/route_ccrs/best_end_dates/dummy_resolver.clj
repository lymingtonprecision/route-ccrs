(ns route-ccrs.best-end-dates.dummy-resolver
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.best-end-dates.protocols :refer :all]))

(def work-centers
  {"MC032" {:id "MC032" :type :internal :hours-per-day 8   :potential-ccr? true}
   "MC008" {:id "MC008" :type :internal :hours-per-day 8   :potential-ccr? true}
   "MC012" {:id "MC012" :type :internal :hours-per-day 12  :potential-ccr? true}
   "IN008" {:id "IN008" :type :internal :hours-per-day 8   :potential-ccr? false}
   "PR7.5" {:id "PR7.5" :type :internal :hours-per-day 7.5 :potential-ccr? false}
   "EXT24" {:id "EXT24" :type :external :hours-per-day 24  :potential-ccr? false}
   "EXT08" {:id "EXT08" :type :external :hours-per-day 8   :potential-ccr? false}})

(defrecord DummyResolver [end-dates interval-factor])

(extend-type DummyResolver
  EndDateResolver
  (shop-order-end-date [this order-id]
    (get (:end-dates this) order-id))
  (purchase-order-end-date [this order-id]
    (get (:end-dates this) order-id))

  IntervalEndDateCalculator
  (interval-end-date
    ([this days]
     (interval-end-date this days (t/today)))
    ([this days start-date]
     (t/plus (tc/to-date-time start-date)
             (t/days (* days (or (:interval-factor this) 1))))))

  ManufacturingEndDateCalculator
  (work-center-end-date
    ([this wc time-at-wc pre-wc-days post-wc-days]
     (work-center-end-date this wc time-at-wc pre-wc-days post-wc-days
                           (t/today)))
    ([this wc time-at-wc pre-wc-days post-wc-days start-date]
     (t/plus (tc/to-date-time start-date)
             (t/days (+ pre-wc-days
                        post-wc-days
                        (/ time-at-wc
                           60
                           (:hours-per-day (work-centers wc)))))))))

(def dummy-resolver (map->DummyResolver {}))
