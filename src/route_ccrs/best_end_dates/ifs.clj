(ns route-ccrs.best-end-dates.ifs
  "An implementation of the best end date protocols using a connection
  to an IFS database."
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [clj-time.coerce :as tc]
            [yesql.core :refer [defquery]]
            [bugsbio.squirrel :as sq]
            [route-ccrs.sql.serializers :refer :all]
            [route-ccrs.schema.dates :refer [Date]]
            [route-ccrs.schema.ids :as ids]
            [route-ccrs.schema.routes :refer [WorkCenterId]]
            [route-ccrs.best-end-dates.protocols :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(def results->end-date
  {:row-fn #(:end-date (sq/to-clj % {:end-date date-serializer}))
   :result-set-fn first})

(defn wrap-db-fn [f]
  (fn [this & args]
    (apply f (:db this) args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; EndDateResolver protocol implementation

(defquery -db-shop-order-end-date "route_ccrs/sql/shop_order_end_date.sql")
(defquery -db-purchase-order-end-date "route_ccrs/sql/purchase_order_end_date.sql")

(s/defn ^:always-validate -ifs-shop-order-end-date :- (s/maybe Date)
  [db order-id :- ids/ShopOrderId]
  (-db-shop-order-end-date (sq/to-sql order-id)
                           (merge results->end-date {:connection db})))

(s/defn ^:always-validate -ifs-purchase-order-end-date :- (s/maybe Date)
  [db order-id :- ids/PurchaseOrderId]
  (-db-purchase-order-end-date (sq/to-sql order-id)
                               (merge results->end-date {:connection db})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IntervalEndDateCalculator protocol implementation

(defquery -db-interval-end-date "route_ccrs/sql/interval_end_date.sql")

(s/defn ^:always-validate -ifs-interval-end-date :- Date
  ([db days] (-ifs-interval-end-date db days nil))
  ([db
    days :- (s/both s/Num (s/pred #(>= % 0)))
    start-date :- (s/maybe Date)]
   (-db-interval-end-date {:duration days
                           :start_date (if start-date
                                         (tc/to-sql-date start-date))}
                          (merge results->end-date {:connection db}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ManufacturingEndDateCalculator protocol implementation

(defquery -db-wc-end-date "route_ccrs/sql/work_center_end_date.sql")

(s/defn ^:always-validate -ifs-work-center-end-date :- (s/maybe Date)
  ([db wc time-at-wc pre-wc-days post-wc-days]
   (-ifs-work-center-end-date db wc time-at-wc pre-wc-days post-wc-days nil))
  ([db
    wc :- WorkCenterId
    time-at-wc :- (s/both s/Int (s/pred #(>= % 0)))
    pre-wc-days :- (s/both s/Num (s/pred #(>= % 0)))
    post-wc-days :- (s/both s/Num (s/pred #(>= % 0)))
    start-date :- (s/maybe Date)]
   (-db-wc-end-date {:work_center wc
                     :total_touch_time time-at-wc
                     :pre_wc_buffer pre-wc-days
                     :post_wc_buffer post-wc-days
                     :start_date (if start-date
                                   (tc/to-sql-date start-date))}
                    (merge results->end-date {:connection db}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defrecord IFSDateCalculator [db])

(extend IFSDateCalculator
  EndDateResolver
  {:shop-order-end-date (wrap-db-fn -ifs-shop-order-end-date)
   :purchase-order-end-date (wrap-db-fn -ifs-purchase-order-end-date)}

  IntervalEndDateCalculator
  {:interval-end-date (wrap-db-fn -ifs-interval-end-date)}

  ManufacturingEndDateCalculator
  {:work-center-end-date (wrap-db-fn -ifs-work-center-end-date)})

(defn ifs-date-calculator []
  (component/using
   (map->IFSDateCalculator {})
   [:db]))
