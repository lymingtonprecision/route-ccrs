(ns route-ccrs.free-capacity
  (:require [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defquery]]))

(defquery free-capacity "route_ccrs/sql/free_capacity.sql")

(defn sorted-capacities []
  (sorted-set-by
    (fn [x y]
      (let [x-s (:start_work_day x)
            y-s (:start_work_day y)
            x-e (:finish_work_day x)
            y-e (:finish_work_day y)
            c-s (compare x-s y-s)]
        (if (= c-s 0)
          (compare x-e y-e)
          c-s)))))

(defn free-capacity-by-work-center [db]
  (reduce
    (fn [r fc]
      (let [wc (:work_center_no fc)
            wcfc (get r wc (sorted-capacities))]
        (assoc r wc (conj wcfc fc))))
    {}
    (free-capacity {} {:connection db})))
