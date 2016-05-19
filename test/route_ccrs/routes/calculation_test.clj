(ns route-ccrs.routes.calculation-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.core :as s]
            [schema.test]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.best-end-dates.dummy-resolver :as dr]
            [route-ccrs.best-end-dates.protocols :refer :all]
            [route-ccrs.routes.calculation :refer :all]))

(use-fixtures :once schema.test/validate-schemas)

(def t' true)
(def f' false)

(def work-centers dr/work-centers)

(defn calculate-buffers [xs]
  (vec
   (map
    (fn [x]
      (let [b (if (= :external (-> x :work-center :type))
                0
                (* (:touch-time x) 0.5))]
        (assoc x :buffer b)))
    xs)))

(defn almost=
  ([^Double x ^Double y]
   (almost= x y 6))
  ([^Double x ^Double y precision]
   (cond
     (some #(.isNaN %) [x y]) (every? #(.isNaN %) [x y])
     (some #(.isInfinite %) [x y]) (every? #(.isInfinite %) [x y])
     :else
     (with-precision precision
       (= (BigDecimal. x *math-context*) (BigDecimal. y *math-context*))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; operation-buffer-days

(defspec operation-buffer-days-is-touch-time-plus-buffer
  (prop/for-all [[tth buf wc] (gen/tuple
                               gen/pos-int
                               (gen/double* {:infinity? false :NaN? false :min 0})
                               (gen/elements
                                (filter #(= :internal (:type %))
                                        (vals work-centers))))]
                (let [o {:id 10
                         :work-center wc
                         :touch-time (* tth 60)
                         :buffer (* buf 60)}]
                  (is (almost=
                       (/ (+ tth buf) (-> o :work-center :hours-per-day))
                       (operation-buffer-days o))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; calculate-route-totals

(deftest simple-route-total
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations
           (calculate-buffers
            [{:id 10 :work-center (work-centers "MC008") :touch-time (* 5 60)}
             {:id 20 :work-center (work-centers "EXT24") :touch-time (* 1 60 24)}
             {:id 30 :work-center (work-centers "IN008") :touch-time (* 1 60)}])}
        ex {:total-touch-time 1800
            :total-buffer 2.125}]
    (is (= ex (calculate-route-totals r)))))

(deftest fractional-touch-time-route-total
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations
           (calculate-buffers
            [{:id 10 :work-center (work-centers "MC008") :touch-time (* 5.33 60)}
             {:id 20 :work-center (work-centers "EXT24") :touch-time (* 3.12 60 24)}
             {:id 30 :work-center (work-centers "IN008") :touch-time (* 1.57 60)}])}
        ex {:total-touch-time 4907
            :total-buffer 4.41375}]
    (s/without-fn-validation
      (is (= ex (calculate-route-totals r))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; potential-ccrs

(def route-without-ccrs
  {:id {:type :manufactured :revision 1 :alternative "*"}
   :operations
   (calculate-buffers
    [{:id 10 :work-center (work-centers "EXT24") :touch-time (* 5 60 24)}
     {:id 20 :work-center (work-centers "IN008") :touch-time (* 10 60)}])})

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
                                             :touch-time (* 4 60)
                                             :buffer 120.0}))
        e {:id "MC008"
           :operation 20
           :total-touch-time 840
           :pre-ccr-buffer 5.0
           :post-ccr-buffer 0.0}]
    (is (= [e] (vec (potential-ccrs r))))))

(deftest potential-ccrs-with-multiple-ccrs
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations
           (calculate-buffers
            [{:id 10 :work-center (work-centers "MC008") :touch-time (* 10 60)}
             {:id 20 :work-center (work-centers "EXT24") :touch-time (* 4 60 24)}
             {:id 30 :work-center (work-centers "MC012") :touch-time (* 3 60)}
             {:id 40 :work-center (work-centers "MC012") :touch-time (* 3 60)}
             {:id 50 :work-center (work-centers "IN008") :touch-time (* 2 60)}
             {:id 60 :work-center (work-centers "MC008") :touch-time (* 3 60)}
             {:id 70 :work-center (work-centers "IN008") :touch-time (* 1 60)}])}
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

(def dc dr/dummy-resolver)
(def work-center-queues dr/work-center-queues)

(deftest route-calculation-no-ccrs
  (let [r route-without-ccrs
        e (assoc route-without-ccrs
                 :ccr nil
                 :ccr-queue 0
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days 6.875)))
                 :total-touch-time 7800
                 :total-buffer 6.875)]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-one-ccr
  (let [r (update-in route-without-ccrs [:operations 0]
                     assoc
                     :work-center (work-centers "MC008")
                     :touch-time (* 64 60)
                     :buffer (* 32 60))
        q (work-center-queues "MC008" 0)
        e (assoc r
                 :ccr {:id "MC008"
                       :operation 10
                       :total-touch-time 3840
                       :pre-ccr-buffer 0
                       :post-ccr-buffer 1.875}
                 :ccr-queue q
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days (+ q 9.875))))
                 :total-touch-time 4440
                 :total-buffer 13.875)]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-multiple-ccrs
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations
           (calculate-buffers
            [{:id 10 :work-center (work-centers "MC008") :touch-time (* 10 60)}
             {:id 20 :work-center (work-centers "EXT24") :touch-time (* 4 60 24)}
             {:id 30 :work-center (work-centers "MC012") :touch-time (* 3 60)}
             {:id 40 :work-center (work-centers "MC012") :touch-time (* 3 60)}
             {:id 50 :work-center (work-centers "IN008") :touch-time (* 2 60)}
             {:id 60 :work-center (work-centers "MC008") :touch-time (* 3 60)}
             {:id 70 :work-center (work-centers "IN008") :touch-time (* 1 60)}])}
        q (work-center-queues "MC012" 0)
        e (assoc r
                 :ccr {:id "MC012"
                       :operation 30
                       :total-touch-time 360
                       :pre-ccr-buffer 5.875
                       :post-ccr-buffer 1.125}
                 :ccr-queue q
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days (+ q 7.5))))
                 :total-touch-time 7080
                 :total-buffer 7.75)]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-calculated-route-without-ops-with-ccr
  (let [q (work-center-queues "MC012" 0)
        r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations []
           :ccr {:id "MC012"
                 :operation 30
                 :total-touch-time 360
                 :pre-ccr-buffer 5.875
                 :post-ccr-buffer 1.125}
           :ccr-queue q
           :best-end-date (tc/to-date-time
                            (t/plus (t/minus (t/today) (t/days 20))
                                    (t/days (+ q 7.5))))
           :total-touch-time 7080
           :total-buffer 7.75}
        e (assoc r
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days (+ q 7.5)))))]
    (is (= e (update-route-calculation r dc)))))

(deftest route-calculation-calculated-route-without-ops-without-ccr
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations []
           :ccr nil
           :ccr-queue 0
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
  (let [q (work-center-queues "MC012")
        r (assoc route-without-ccrs
                 :ccr {:id "MC012"
                       :operation 30
                       :total-touch-time 360
                       :pre-ccr-buffer 5.875
                       :post-ccr-buffer 1.125}
                 :ccr-queue q
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/minus (t/today) (t/days 20))
                                          (t/days (+ q 32.275))))
                 :total-touch-time 13527
                 :total-buffer 32.275)
        e (assoc r
                 :ccr nil
                 :ccr-queue 0
                 :best-end-date (tc/to-date-time
                                  (t/plus (t/today) (t/days 6.875)))
                 :total-touch-time 7800
                 :total-buffer 6.875)]
    (is (= e (update-route-calculation r dc)))))
