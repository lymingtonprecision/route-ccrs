(ns route-ccrs.best-end-dates-test
  #?(:cljs (:require-macros [cljs.test :refer [use-fixtures deftest is]]))
  (:require #?(:clj  [clojure.test :refer [use-fixtures deftest is]]
               :cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            #?(:clj  [clj-time.core :as t]
               :cljs [cljs-time.core :as t])
            [schema.test]
            [route-ccrs.generators.raw-part :refer [gen-raw-part]]
            [route-ccrs.best-end-dates
             :refer [best-end-date remove-best-end-dates]]))

(use-fixtures :once schema.test/validate-schemas)

(defn rand-date [] (t/plus (t/today) (t/days (rand-int 999))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; best-end-date

(defspec raw-part-best-end-date
  (prop/for-all [p (gen-raw-part)]
                (is (= (:best-end-date p) (best-end-date p)))))

(deftest purchased-structure-best-end-date
  (let [d (rand-date)
        ps {:id {:type :purchased :revision 1 :alternative "*"}
            :lead-time 10
            :components {}
            :best-end-date nil}]
    (is (nil? (best-end-date ps)))
    (is (= d (best-end-date (assoc ps :best-end-date d))))))

(deftest uncalculated-route-best-end-date
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :operations [{:id 10
                         :work-center {:id "MC032"
                                       :type :internal
                                       :hours-per-day 8
                                       :potential-ccr? true}
                         :touch-time 10
                         :buffer 5}]}]
    (is (nil? (best-end-date r)))))

(deftest calculated-route-best-end-date
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :best-end-date (rand-date)
           :ccr nil
           :ccr-queue 0
           :total-touch-time 10
           :total-buffer 5
           :operations [{:id 10
                         :work-center {:id "MC032"
                                       :type :internal
                                       :hours-per-day 8
                                       :potential-ccr? true}
                         :touch-time 10
                         :buffer 5}]}]
    (is (= (:best-end-date r) (best-end-date r)))))

(deftest manufactured-structure-best-end-date
  (let [o {:id 10 :touch-time 10 :buffer 5
           :work-center {:id "MC032"
                         :type :internal
                         :hours-per-day 8
                         :potential-ccr? true}}
        d (rand-date)
        s {:id {:type :manufactured :revision 1 :alternative "*"}
           :components {}
           :route-in-use 812
           :routes
           {1 {:id {:type :manufactured :revision 1 :alternative "*"}
               :best-end-date (t/today)
               :ccr nil
               :ccr-queue 0
               :total-touch-time 10
               :total-buffer 5
               :operations [o]}
            812 {:id {:type :manufactured :revision 1 :alternative "1"}
                 :operations [o]}
            999 {:id {:type :manufactured :revision 2 :alternative "*"}
                 :best-end-date (t/minus d (t/days (rand-int 99)))
                 :ccr nil
                 :ccr-queue 0
                 :total-touch-time 10
                 :total-buffer 5
                 :operations [o]}}}]
    (is (nil? (best-end-date s)))
    (is (= d (best-end-date (update-in s [:routes 812]
                                       assoc
                                       :best-end-date d
                                       :ccr nil
                                       :ccr-queue 0
                                       :total-touch-time 10
                                       :total-buffer 5))))))

(deftest structured-part-best-end-date
  (let [o {:id 10 :touch-time 10 :buffer 5
           :work-center {:id "MC032"
                         :type :internal
                         :hours-per-day 8
                         :potential-ccr? true}}
        d1 (rand-date)
        d2 (rand-date)
        p {:id "100105644R01"
           :type :structured
           :best-end-date nil
           :struct-in-use 140
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :best-end-date d1
               :lead-time 10
               :components {}}
            140 {:id {:type :manufactured :revision 1 :alternative "*"}
                 :components {}
                 :route-in-use 18
                 :routes
                 {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                     :best-end-date d1
                     :ccr nil
                     :ccr-queue 0
                     :total-touch-time 10
                     :total-buffer 5
                     :operations [o]}
                  18 {:id {:type :manufactured :revision 2 :alternative "*"}
                      :operations [o]}}}}}]
    (is (nil? (best-end-date p)))
    (is (= d1
           (best-end-date (assoc p :source [:fictitious] :best-end-date d1))))
    (is (= d1
           (best-end-date (assoc p :struct-in-use 1))))
    (is (= d2
           (best-end-date (update-in p [:structs 140 :routes 18]
                                     assoc
                                     :best-end-date d2
                                     :ccr nil
                                     :ccr-queue 0
                                     :total-touch-time 10
                                     :total-buffer 5))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; remove-best-end-dates

(defspec raw-part-end-date-removal
  (prop/for-all [p (gen-raw-part)]
                (is (= (assoc p :best-end-date nil)
                       (remove-best-end-dates p)))))

(deftest purchased-structure-best-end-date-removal
  (let [s {:id "100102457R01"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10
               :best-end-date (t/today)
               :components {}}}}
        ex (assoc-in s [:structs 1 :best-end-date] nil)]
    (is (= ex (remove-best-end-dates s)))))

(deftest routing-best-end-date-removal
  (let [r {:id "100120785R01"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :manufactured :revision 1 :alternative "*"}
               :components {}
               :route-in-use 1
               :routes
               {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                   :operations [{:id 10
                                 :work-center {:id "MC032"
                                               :type :internal
                                               :hours-per-day 8
                                               :potential-ccr? true}
                                 :touch-time 10
                                 :buffer 5}]}}}}}
        red (update-in r [:structs 1 :routes 1] assoc
                       :best-end-date (t/today)
                       :ccr nil
                       :ccr-queue 0
                       :total-touch-time 10
                       :total-buffer 20)]
    (is (= r (remove-best-end-dates red)))))
