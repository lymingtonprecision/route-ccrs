(ns route-ccrs.routes.calculation-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.core :as s]
            [schema.test]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.best-end-dates.protocols :refer :all]
            [route-ccrs.routes.calculation :refer :all]))

(use-fixtures :once schema.test/validate-schemas)

(def t' true)
(def f' false)

(def work-centers
  {"MC008" {:id "MC008" :type :internal :hours-per-day 8   :potential-ccr? t'}
   "MC012" {:id "MC012" :type :internal :hours-per-day 12  :potential-ccr? t'}
   "IN008" {:id "IN008" :type :internal :hours-per-day 8   :potential-ccr? f'}
   "PR7.5" {:id "PR7.5" :type :internal :hours-per-day 7.5 :potential-ccr? f'}
   "EXT24" {:id "EXT24" :type :external :hours-per-day 24  :potential-ccr? f'}
   "EXT08" {:id "EXT08" :type :external :hours-per-day 8   :potential-ccr? f'}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; operation-buffer-days

(defspec internal-work-center-operation-buffer-days
  (prop/for-all [[tth wc] (gen/tuple
                           gen/pos-int
                           (gen/elements
                             (filter #(= :internal (:type %))
                                     (vals work-centers))))]
                (let [o {:id 10 :work-center wc :touch-time (* tth 60)}]
                  (is (= (/ (* tth 1.5) (-> o :work-center :hours-per-day))
                         (operation-buffer-days o))))))

(defspec external-work-center-operation-buffer-days
  (prop/for-all [[tth wc] (gen/tuple
                           gen/pos-int
                           (gen/elements
                             (filter #(= :external (:type %))
                                     (vals work-centers))))]
                (let [o {:id 10 :work-center wc :touch-time (* tth 60)}]
                  (is (= (/ (* tth 1.0) (-> o :work-center :hours-per-day))
                         (operation-buffer-days o))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; calculate-route-totals

(deftest simple-route-total
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations
           [{:id 10 :work-center (work-centers "MC008") :touch-time (* 5 60)}
            {:id 20 :work-center (work-centers "EXT24") :touch-time (* 1 60 24)}
            {:id 30 :work-center (work-centers "IN008") :touch-time (* 1 60)}]}
        ex {:total-touch-time 1800
            :total-buffer 2.125}]
    (is (= ex (calculate-route-totals r)))))

(deftest fractional-touch-time-route-total
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations
           [{:id 10 :work-center (work-centers "MC008") :touch-time (* 5.33 60)}
            {:id 20 :work-center (work-centers "EXT24") :touch-time (* 3.12 60 24)}
            {:id 30 :work-center (work-centers "IN008") :touch-time (* 1.57 60)}]}
        ex {:total-touch-time 4907
            :total-buffer 4.41375}]
    (s/without-fn-validation
      (is (= ex (calculate-route-totals r))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; potential-ccrs

(def route-without-ccrs
  {:id {:type :manufactured :revision 1 :alternative "*"}
   :operations
   [{:id 10 :work-center (work-centers "EXT24") :touch-time (* 5 60 24)}
    {:id 20 :work-center (work-centers "IN008") :touch-time (* 10 60)}]})

(deftest potential-ccrs-for-route-without-potential-ccrs
  (let [r route-without-ccrs]
    (is (not (seq? (potential-ccrs r))))))

(deftest potential-ccrs-with-one-ccr-op
  (let [r (assoc-in route-without-ccrs [:operations 1 :work-center]
                    (work-centers "MC008"))
        e {:id "MC008"
           :operation 20
           :total-touch-time 600
           :pre-ccr-buffer 5.0
           :post-ccr-buffer 0.0}]
    (is (= [e] (vec (potential-ccrs r))))))

(deftest potential-ccrs-with-multiple-ops-on-same-wc
  (let [r (-> route-without-ccrs
              (assoc-in [:operations 1 :work-center] (work-centers "MC008"))
              (update-in [:operations] conj {:id 40
                                             :work-center (work-centers "MC008")
                                             :touch-time (* 4 60)}))
        e {:id "MC008"
           :operation 20
           :total-touch-time 840
           :pre-ccr-buffer 5.0
           :post-ccr-buffer 0.0}]
    (is (= [e] (vec (potential-ccrs r))))))

(deftest potential-ccrs-with-multiple-ccrs
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations
           [{:id 10 :work-center (work-centers "MC008") :touch-time (* 10 60)}
            {:id 20 :work-center (work-centers "EXT24") :touch-time (* 4 60 24)}
            {:id 30 :work-center (work-centers "MC012") :touch-time (* 3 60)}
            {:id 40 :work-center (work-centers "MC012") :touch-time (* 3 60)}
            {:id 50 :work-center (work-centers "IN008") :touch-time (* 2 60)}
            {:id 60 :work-center (work-centers "MC008") :touch-time (* 3 60)}
            {:id 70 :work-center (work-centers "IN008") :touch-time (* 1 60)}]}
        e [{:id "MC008"
            :operation 10
            :total-touch-time 780
            :pre-ccr-buffer 0
            :post-ccr-buffer 5.3125}
           {:id "MC012"
            :operation 30
            :total-touch-time 360
            :pre-ccr-buffer 5.875
            :post-ccr-buffer 1.125}]]
    (is (= (sort-by :id e) (->> (potential-ccrs r) vec (sort-by :id))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update-route-calculation

(defrecord TestDateCalculator [interval-factor])

(extend-type TestDateCalculator
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
     (let [wc (work-centers wc)]
       (t/plus (tc/to-date-time start-date)
               (t/days (+ pre-wc-days post-wc-days
                          (/ time-at-wc 60 (:hours-per-day wc)))))))))

(def dc (map->TestDateCalculator {}))

(deftest route-calculation-no-ccrs
  (let [r route-without-ccrs
        e (assoc route-without-ccrs
                 :ccr nil
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days 6.875)))
                 :total-touch-time 7800
                 :total-buffer 6.875)]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-one-ccr
  (let [r (update-in route-without-ccrs [:operations 0]
                     assoc
                     :work-center (work-centers "MC008")
                     :touch-time (* 64 60))
        e (assoc r
                 :ccr {:id "MC008"
                       :operation 10
                       :total-touch-time 3840
                       :pre-ccr-buffer 0
                       :post-ccr-buffer 1.875}
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days 9.875)))
                 :total-touch-time 4440
                 :total-buffer 13.875)]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-multiple-ccrs
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations
           [{:id 10 :work-center (work-centers "MC008") :touch-time (* 10 60)}
            {:id 20 :work-center (work-centers "EXT24") :touch-time (* 4 60 24)}
            {:id 30 :work-center (work-centers "MC012") :touch-time (* 3 60)}
            {:id 40 :work-center (work-centers "MC012") :touch-time (* 3 60)}
            {:id 50 :work-center (work-centers "IN008") :touch-time (* 2 60)}
            {:id 60 :work-center (work-centers "MC008") :touch-time (* 3 60)}
            {:id 70 :work-center (work-centers "IN008") :touch-time (* 1 60)}]}
        e (assoc r
                 :ccr {:id "MC012"
                       :operation 30
                       :total-touch-time 360
                       :pre-ccr-buffer 5.875
                       :post-ccr-buffer 1.125}
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days 7.5)))
                 :total-touch-time 7080
                 :total-buffer 7.75)]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-calculated-route-without-ops-with-ccr
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations []
           :ccr {:id "MC012"
                 :operation 30
                 :total-touch-time 360
                 :pre-ccr-buffer 5.875
                 :post-ccr-buffer 1.125}
           :best-end-date (tc/to-date-time
                            (t/plus (t/minus (t/today) (t/days 20))
                                    (t/days 7.5)))
           :total-touch-time 7080
           :total-buffer 7.75}
        e (assoc r
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days 7.5))))]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-calculated-route-without-ops-without-ccr
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations []
           :ccr nil
           :best-end-date (tc/to-date-time
                            (t/plus (t/minus (t/today) (t/days 20))
                                    (t/days 32.275)))
           :total-touch-time 13527
           :total-buffer 32.275}
        e (assoc r
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days 32.275))))]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-prefers-calculating-from-ops
  (let [r (assoc route-without-ccrs
                 :ccr {:id "MC012"
                       :operation 30
                       :total-touch-time 360
                       :pre-ccr-buffer 5.875
                       :post-ccr-buffer 1.125}
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/minus (t/today) (t/days 20))
                                          (t/days 32.275)))
                 :total-touch-time 13527
                 :total-buffer 32.275)
        e (assoc r
                 :ccr nil
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days 6.875)))
                 :total-touch-time 7800
                 :total-buffer 6.875)]
    (is (= e (update-route-calculation r dc)))))
