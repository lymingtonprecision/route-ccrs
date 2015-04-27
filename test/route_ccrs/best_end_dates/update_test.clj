(ns route-ccrs.best-end-dates.update-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [schema.core :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.generators.raw-part :refer [gen-raw-part]]
            [route-ccrs.best-end-dates.dummy-resolver :refer :all]
            [route-ccrs.best-end-dates.protocols :refer :all]
            [route-ccrs.best-end-dates :refer [remove-best-end-dates]]
            [route-ccrs.best-end-dates.update :refer :all]))

(defn rand-days-from-date
  ([] (rand-days-from-date (t/today)))
  ([sd] (t/plus sd (t/days (rand-int 999)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update-best-end-date

(deftest update-end-date-throws-arg-exception-on-invalid-input
  (s/without-fn-validation
   (is (thrown? IllegalArgumentException
                (update-best-end-date 'invalid dummy-resolver)))))

(deftest updating-a-sourced-end-date
  (let [d (rand-days-from-date)
        p {:id "100102791R01" :type :raw
           :lead-time 10
           :best-end-date nil
           :source [:fictitious]}]
    (is (= d (:best-end-date (update-best-end-date p dummy-resolver d))))
    (is (= (t/today) (:best-end-date (update-best-end-date p dummy-resolver))))))

(deftest updating-a-purchased-end-date
  (let [d (rand-days-from-date)
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
  (let [d (rand-days-from-date)
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
        d (rand-days-from-date)
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
        d (rand-days-from-date)
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
        d (rand-days-from-date)
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
        d (rand-days-from-date)
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
