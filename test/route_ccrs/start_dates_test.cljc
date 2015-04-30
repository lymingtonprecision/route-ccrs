(ns route-ccrs.start-dates-test
  #?(:cljs (:require-macros [cljs.test :refer [use-fixtures deftest is]]))
  (:require #?(:clj  [clojure.test :refer [use-fixtures deftest is]]
               :cljs [cljs.test.check :refer [quick-check]])
            [schema.test]
            #?@(:clj  [[clj-time.core :as t]
                       [clj-time.coerce :as tc]]
                :cljs [[cljs-time.core :as t]
                       [cljs-time.coerce :as tc]
                       [cljs-time.extend]])
            [route-ccrs.start-dates :refer [start-date]]))

(use-fixtures :once schema.test/validate-schemas)

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
  (let [r {:id "100117890R20" :type :raw :lead-time 10 :best-end-date nil}
        d (gen-date)]
    (is (= (t/today) (start-date r)))
    (is (= d (start-date r d)))))

(deftest start-date-of-an-empty-structure
  (let [r {:id {:type :purchased :revision 1 :alternative "*"}
           :lead-time 10
           :best-end-date nil
           :components {}}
        d (gen-date)]
    (is (= (t/today) (start-date r)))
    (is (= d (start-date r d)))))

(deftest start-date-structure-without-component-end-dates
  (let [r {:id {:type :purchased :revision 1 :alternative "*"}
           :lead-time 10
           :best-end-date nil
           :components
           {1 {:id "100157802R02" :type :raw :lead-time 10 :best-end-date nil}
            2 {:id "100157801R02" :type :raw :lead-time 20 :best-end-date nil}}}
        d (gen-date)]
    (is (= (t/today) (start-date r)))
    (is (= d (start-date r d)))))

(deftest start-date-of-a-structure-with-component-end-dates
  (let [d1 (gen-date)
        d2 (gen-date)
        d3 (gen-date)
        e (tc/to-date-time (t/latest d1 d2))
        r {:id {:type :purchased :revision 1 :alternative "*"}
           :lead-time 10
           :best-end-date nil
           :components
           {1 {:id "100157802R02" :type :raw :lead-time 10 :best-end-date d1}
            2 {:id "100157801R02" :type :raw :lead-time 20 :best-end-date nil}
            3 {:id "100159020R02" :type :raw :lead-time 20 :best-end-date d2}}}]
    (is (= e (start-date r)))
    (is (= e (start-date r d3)))))

(deftest start-date-of-a-structured-part
  (let [d1 (gen-date)
        d2 (gen-date)
        d3 (gen-date)
        p {:id "100125902R04"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10
               :best-end-date nil
               :components
               {1 {:id "100157802R02" :type :raw :lead-time 10 :best-end-date d1}
                2 {:id "100157801R02" :type :raw :lead-time 20 :best-end-date nil}
                3 {:id "100159020R02" :type :raw :lead-time 20 :best-end-date d2}}}
            2 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10
               :best-end-date nil
               :components {}}}}]
    (is (= (tc/to-date-time (t/latest d1 d2)) (start-date p)))
    (is (= (t/today) (start-date (assoc p :struct-in-use 2))))
    (is (= d3 (start-date (assoc p :struct-in-use 2) d3)))))

(deftest start-date-of-a-sourced-structured-part
  (let [e (gen-date)
        r {:id "100178605R01"
           :type :structured
           :best-end-date nil
           :source [:fixed-leadtime (rand-int 999)]
           :struct-in-use 1
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10
               :best-end-date nil
               :components
               {1 {:id "100123486R01"
                   :type :raw
                   :lead-time 10
                   :best-end-date (t/plus e (t/days (rand-int 999)))}}}}}]
    (is (= e (start-date r e)))))
