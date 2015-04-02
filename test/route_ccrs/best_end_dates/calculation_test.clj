(ns route-ccrs.best-end-dates.calculation-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [schema.test]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.best-end-dates :refer [remove-best-end-dates]]
            [route-ccrs.best-end-dates.protocols :refer :all]
            [route-ccrs.best-end-dates.calculation :refer :all]))

(use-fixtures :once schema.test/validate-schemas)

(defn gen-date
  ([] (gen-date (t/today)))
  ([sd] (t/plus sd (t/days (rand-int 999)))))

(def work-centers
  {"MC032" {:id "MC032" :type :internal :hours-per-day 8 :potential-ccr? true}})

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; end-date-from-source

(deftest non-schema-validated-non-source-record-returns-nil
  (s/without-fn-validation
    (is (nil? (end-date-from-source {:has-no :source} dummy-resolver)))))

(deftest non-schema-validated-invalid-source-throws-arg-exception
  (s/without-fn-validation
    (is (thrown? IllegalArgumentException
                 (end-date-from-source {:source 'invalid} dummy-resolver)))))

(deftest fictitious-sources-end-on-today
  (is (= (t/today) (end-date-from-source {:source [:fictitious]} dummy-resolver))))

(deftest fictitious-sources-end-on-start-date-when-provided
  (let [d (gen-date)]
    (is (= d (end-date-from-source {:source [:fictitious]} dummy-resolver d)))))

(deftest stock-sources-end-on-today
  (is (= (t/today) (end-date-from-source {:source [:stock]} dummy-resolver))))

(deftest stock-sources-end-on-start-date-when-provided
  (let [d (gen-date)]
    (is (= d (end-date-from-source {:source [:stock]} dummy-resolver d)))))

(deftest leadtime-sources-are-interval-calculated
  (is (= (interval-end-date dummy-resolver 10)
         (end-date-from-source {:source [:fixed-leadtime 10]} dummy-resolver)))
  (let [dr (assoc dummy-resolver :interval-factor (rand-int 99))]
    (is (= (interval-end-date dr 10)
           (end-date-from-source {:source [:fixed-leadtime 10]} dr)))))

(deftest leadtime-sources-are-calculated-from-start-date
  (let [d (gen-date)
        i (rand-int 999)]
    (is (= (interval-end-date dummy-resolver i d)
           (end-date-from-source
             {:source [:fixed-leadtime i]} dummy-resolver d)))))

(deftest shop-order-sources-are-resolved
  (let [id {:order-no "1234" :release "*" :sequence "*"}
        d (gen-date)
        dr (assoc dummy-resolver :end-dates {id d})]
    (is (= d (end-date-from-source {:source [:shop-order id]} dr)))
    (is (nil? (end-date-from-source
                {:source [:shop-order
                          {:order-no "L1234" :release "1" :sequence "1"}]}
                dr)))))

(deftest purchase-order-sources-are-resolved
  (let [id {:order-no "1234" :line 1 :release 1}
        d (gen-date)
        dr (assoc dummy-resolver :end-dates {id d})]
    (is (= d (end-date-from-source {:source [:purchase-order id]} dr)))
    (is (nil? (end-date-from-source
                {:source [:purchase-order
                          {:order-no "12301481" :line 12 :release 4}]}
                dr)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update-best-end-date

(deftest update-end-date-throws-arg-exception-on-invalid-input
  (s/without-fn-validation
    (is (thrown? IllegalArgumentException
                 (update-best-end-date 'invalid dummy-resolver)))))

(deftest updating-a-sourced-end-date
  (let [d (gen-date)
        p {:id "100102791R01" :type :raw
           :lead-time 10
           :best-end-date nil
           :source [:fictitious]}]
    (is (= d (:best-end-date (update-best-end-date p dummy-resolver d))))
    (is (= (t/today) (:best-end-date (update-best-end-date p dummy-resolver))))))

(deftest updating-a-purchased-end-date
  (let [d (gen-date)
        lt (rand-int 999)]
    (is (= (interval-end-date dummy-resolver lt d)
           (:best-end-date (update-best-end-date
                             {:id "100117804R28"
                              :type :raw
                              :lead-time lt
                              :best-end-date nil}
                             dummy-resolver
                             d))))
    (is (= (interval-end-date dummy-resolver lt d)
           (:best-end-date
             (update-best-end-date
               {:id {:type :purchased :revision 1 :alternative "*"}
                :lead-time lt
                :best-end-date nil
                :components {}}
               dummy-resolver
               d))))))

(deftest updating-a-structured-part-end-date-using-a-purchased-struct
  (let [d (gen-date)
        lt (rand-int 9999)
        p {:id "100128714R12"
           :type :structured
           :best-end-date nil
           :struct-in-use "2c89"
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 189 :best-end-date nil :components {}}
            "2c89" {:id {:type :purchased :revision 2 :alternative "*"}
                    :lead-time lt :best-end-date nil :components {}}
            9999 {:id {:type :manufactured :revision 1 :alternative "*"}
                  :components {}
                  :route-in-use 1
                  :routes
                  {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                      :operations [{:id 10
                                    :work-center {:id "MC032"
                                                  :type :internal
                                                  :hours-per-day 8
                                                  :potential-ccr? true}
                                    :touch-time 10}]}}}}}
        ex (assoc-in p [:structs "2c89" :best-end-date]
                     (interval-end-date dummy-resolver lt d))]
    (is (= ex (update-best-end-date p dummy-resolver d)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update-all-best-end-dates-under-part

(deftest update-all-with-just-a-raw-part
  (let [lt (rand-int 999)
        p {:id "100108675R01" :type :raw :lead-time lt :best-end-date nil}
        d (gen-date)
        e (assoc p :best-end-date (tc/to-date-time (t/plus d (t/days lt))))]
    (is (= e (update-all-best-end-dates-under-part p dummy-resolver d)))))

(deftest update-all-with-empty-structure
  (let [lt (rand-int 999)
        p {:id "100168706R02"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :manufactured :revision 1 :alternative "*"}
               :components {}
               :route-in-use 1
               :routes
               {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                   :operations
                   [{:id 10 :touch-time (* 10 60)
                     :work-center (work-centers "MC032")}]}}}
            2 {:id {:type :purchased :revision 1 :alternative "*"}
               :components {} :lead-time lt :best-end-date nil}}}
        d (gen-date)
        e (-> p
              (assoc-in
                [:structs 2 :best-end-date]
                (tc/to-date-time (t/plus d (t/days lt))))
              (update-in
                [:structs 1 :routes 1]
                assoc
                :total-touch-time 600
                :total-buffer 1.875
                :best-end-date (tc/to-date-time (t/plus d (t/days 1.875)))
                :ccr {:id "MC032"
                      :operation 10
                      :total-touch-time 600
                      :pre-ccr-buffer 0
                      :post-ccr-buffer 0.0}))]
    (is (= e (update-all-best-end-dates-under-part p dummy-resolver d)))))

(deftest update-all-with-simple-structure
  (let [lt (rand-int 999)
        p {:id "100168706R02"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :manufactured :revision 1 :alternative "*"}
               :components
               {1 {:id "100108678R01"
                   :type :raw
                   :lead-time lt
                   :best-end-date nil}}
               :route-in-use 1
               :routes
               {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                   :operations
                   [{:id 10 :touch-time (* 10 60)
                     :work-center (work-centers "MC032")}]}}}}}
        d (gen-date)
        e (-> p
              (assoc-in
                [:structs 1 :components 1 :best-end-date]
                (tc/to-date-time (t/plus d (t/days lt))))
              (update-in
                [:structs 1 :routes 1]
                assoc
                :total-touch-time 600
                :total-buffer 1.875
                :best-end-date (tc/to-date-time
                                 (t/plus
                                   (t/plus d (t/days lt))
                                   (t/days 1.875)))
                :ccr {:id "MC032"
                      :operation 10
                      :total-touch-time 600
                      :pre-ccr-buffer 0
                      :post-ccr-buffer 0.0}))]
    (is (= e (update-all-best-end-dates-under-part p dummy-resolver d)))))

(deftest update-all-with-detailed-structure
  (let [lt (rand-int 999)
        ltd (t/days lt)
        d (gen-date)
        sd (tc/to-date-time (t/plus d ltd))
        e {:id "100168706R02"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :manufactured :revision 1 :alternative "*"}
               :components
               {1 {:id "100168708R01"
                   :type :structured
                   :best-end-date nil
                   :struct-in-use 1
                   :structs
                   {1 {:id {:type :purchased :revision 1 :alternative "*"}
                       :lead-time lt
                       :best-end-date (tc/to-date-time
                                        (t/plus sd (t/days (* lt 2))))
                       :components
                       {1 {:id "100169816R52"
                           :type :structured
                           :best-end-date nil
                           :struct-in-use 1
                           :structs
                           {1 {:id {:type :purchased
                                    :revision 1
                                    :alternative "*"}
                               :lead-time lt
                               :best-end-date (tc/to-date-time (t/plus sd ltd))
                               :components
                               {1 {:id "100199763R01"
                                   :type :raw
                                   :lead-time lt
                                   :best-end-date sd}}}}}
                        2 {:id "100189568R78"
                           :type :raw
                           :lead-time (* 9 100 100)
                           :best-end-date d
                           :source [:fictitious]}}}}}
                2 {:id "100190168R01"
                   :type :structured
                   :best-end-date sd
                   :source [:fixed-leadtime lt]
                   :struct-in-use 1
                   :structs
                   {1 {:id {:type :purchased :revision 1 :alternative "*"}
                       :lead-time lt
                       :best-end-date (tc/to-date-time
                                        (t/plus d (t/days (* lt 3))))
                       :components
                       {1 {:id "100169837R02"
                           :type :raw
                           :lead-time (* lt 2)
                           :best-end-date (tc/to-date-time
                                            (t/plus d (t/days (* lt 2))))}}}}}
                3 {:id "100108678R01"
                   :type :raw
                   :lead-time lt
                   :best-end-date sd}}
               :route-in-use 1
               :routes
               {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                   :ccr {:id "MC032"
                         :operation 10
                         :total-touch-time 600
                         :pre-ccr-buffer 0
                         :post-ccr-buffer 0.0}
                   :best-end-date (tc/to-date-time
                                    (t/plus sd (t/days (+ 1.25 (* lt 2)))))
                   :total-touch-time 600
                   :total-buffer 1.875
                   :operations
                   [{:id 10 :touch-time (* 10 60)
                     :work-center (work-centers "MC032")}]}}}}}
        p (remove-best-end-dates e)]
    (is (not= e p))
    (is (= e (update-all-best-end-dates-under-part p dummy-resolver d)))))
