(ns route-ccrs.start-dates-test
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.start-dates :refer :all]))

(defn gen-date [] (t/plus (t/today) (t/days (rand-int 999))))

(deftest start-date-is-nil-for-invalid-record
  (is (nil? (start-date 'invalid))))

(deftest start-date-of-a-route
  (is (= (t/today)
         (start-date {:id {:type :manufactured :revision 1 :alternative "*"}
                      :operations [{:id 10
                                    :work-center {:id "MC032"
                                                  :type :internal
                                                  :hours-per-day 8
                                                  :potential-ccr? true}
                                    :touch-time 10}]}))))

(deftest start-date-of-a-raw-part
  (is (= (t/today)
         (start-date {:id "100117890R20"
                      :type :raw
                      :lead-time 10
                      :best-end-date nil}))))

(deftest start-date-of-an-empty-structure
  (is (= (t/today)
         (start-date {:id {:type :purchased :revision 1 :alternative "*"}
                      :lead-time 10
                      :best-end-date nil
                      :components {}}))))

(deftest start-date-structure-without-component-end-dates
  (is (= (t/today)
         (start-date {:id {:type :purchased :revision 1 :alternative "*"}
                      :lead-time 10
                      :best-end-date nil
                      :components {1 {:id "100157802R02"
                                      :type :raw
                                      :lead-time 10
                                      :best-end-date nil}
                                   2 {:id "100157801R02"
                                      :type :raw
                                      :lead-time 20
                                      :best-end-date nil}}}))))

(deftest start-date-of-a-structure-with-component-end-dates
  (let [d1 (gen-date)
        d2 (gen-date)]
    (is (= (tc/to-date-time (t/latest d1 d2))
         (start-date {:id {:type :purchased :revision 1 :alternative "*"}
                      :lead-time 10
                      :best-end-date nil
                      :components {1 {:id "100157802R02"
                                      :type :raw
                                      :lead-time 10
                                      :best-end-date d1}
                                   2 {:id "100157801R02"
                                      :type :raw
                                      :lead-time 20
                                      :best-end-date nil}
                                   3 {:id "100159020R02"
                                      :type :raw
                                      :lead-time 20
                                      :best-end-date d2}}})))))

(deftest start-date-of-a-structured-part
  (let [d1 (gen-date)
        d2 (gen-date)
        p {:id "100125902R04"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10
               :best-end-date nil
               :components {1 {:id "100157802R02"
                               :type :raw
                               :lead-time 10
                               :best-end-date d1}
                            2 {:id "100157801R02"
                               :type :raw
                               :lead-time 20
                               :best-end-date nil}
                            3 {:id "100159020R02"
                               :type :raw
                               :lead-time 20
                               :best-end-date d2}}}
            2 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10
               :best-end-date nil
               :components {}}}}]
    (is (= (tc/to-date-time (t/latest d1 d2)) (start-date p)))
    (is (= (t/today) (start-date (assoc p :struct-in-use 2))))))
