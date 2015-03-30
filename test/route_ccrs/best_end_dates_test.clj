(ns route-ccrs.best-end-dates-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.properties :as prop']
            [schema.core :as s]
            [schema.test]
            [clj-time.core :as t]
            [route-ccrs.schema.purchased-raw-part-test :refer [gen-raw-part]]
            [route-ccrs.schema.structured-part-test :as spt]
            [route-ccrs.schema.structures.manufacturing-test :as mpt]
            [route-ccrs.schema.parts :refer [Part]]
            [route-ccrs.best-end-dates :refer :all]))

(def ^:dynamic *max-multilevel-depth* 5)

(use-fixtures :once schema.test/validate-schemas)

(defn gen-date [] (t/plus (t/today) (t/days (rand-int 100))))

(defn gen-complex-struct-with-dates []
  (let [d (repeatedly 5 gen-date)
        s {:id "100102347R01"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :manufactured :revision 1 :alternative "*"}
               :route-in-use 1
               :routes
               {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                   :best-end-date (nth d 0)
                   :ccr nil
                   :total-touch-time 50
                   :total-buffer 5
                   :operations [{:id 10
                                 :work-center {:id "MC032"
                                               :type :internal
                                               :hours-per-day 8
                                               :potential-ccr? true}
                                 :touch-time 10}]}}
               :components
               {1 {:id "100110028R01"
                   :type :raw
                   :lead-time 10
                   :best-end-date nil}
                2 {:id "100118022R01"
                   :type :raw
                   :lead-time 10
                   :best-end-date (nth d 1)}
                3 {:id "100112049R02"
                   :type :structured
                   :best-end-date nil
                   :struct-in-use 1
                   :structs
                   {1 {:id {:type :purchased :revision 1 :alternative "*"}
                       :lead-time 10
                       :best-end-date (nth d 2)
                       :components {}}
                    2 {:id {:type :manufactured :revision 1 :alternative "1"}
                       :route-in-use 1
                       :routes
                       {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                           :best-end-date (nth d 3)
                           :ccr nil
                           :total-touch-time 50
                           :total-buffer 5
                           :operations [{:id 10
                                         :work-center {:id "OW003"
                                                       :type :external
                                                       :hours-per-day 24
                                                       :potential-ccr? false}
                                         :touch-time 240}]}}
                       :components
                       {1 {:id "100120035R01"
                           :type :raw
                           :lead-time 5
                           :best-end-date (nth d 4)}}}}}}}}}
        bed {"100102347R01"
             {{:type :manufactured :revision 1 :alternative "*"}
              {{:type :manufactured :revision 1 :alternative "*"}
               {:best-end-date (nth d 0)}}}
             "100118022R01"
             {:best-end-date (nth d 1)}
             "100112049R02"
             {{:type :purchased :revision 1 :alternative "*"}
              {:best-end-date (nth d 2)}
              {:type :manufactured :revision 1 :alternative "1"}
              {{:type :manufactured :revision 1 :alternative "*"}
               {:best-end-date (nth d 3)}}}
             "100120035R01"
             {:best-end-date (nth d 4)}}]
    [s d bed]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; best-end-dates
(defspec best-end-dates-returns-raw-part-end-date
  (prop/for-all [p (gen-raw-part)]
                (let [ex (if (:best-end-date p)
                           {(:id p) {:best-end-date (:best-end-date p)}}
                           {})]
                  (is (= ex (best-end-dates p))))))

(defn end-dates [c]
  (reduce
   (fn [r e]
     (if-let [ed (:best-end-date e)]
       (assoc r (:id e) ed)
       r))
   {}
   c))

(defn structure-end-dates [p]
  (->> p :structs vals end-dates))

(defn route-end-dates [p]
  (->> p :structs vals
       (map #(->> % :routes vals))
       (apply concat)
       (map best-end-dates)
       (apply merge)))

(defn dissoc-nils [m]
  (reduce
   (fn [r [k v]]
     (if (nil? v)
       r
       (assoc r k v)))
   {}
   m))

(deftest best-end-dates-returns-entries-from-all-levels
  (let [[s d ex] (gen-complex-struct-with-dates)]
    (is (= ex (best-end-dates s)))))

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
               :best-end-date (java.util.Date.)
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
                                 :touch-time 10}]}}}}}
        red (update-in r [:structs 1 :routes 1] assoc
                       :best-end-date (java.util.Date.)
                       :ccr nil
                       :total-touch-time 10
                       :total-buffer 20)]
    (is (= r (remove-best-end-dates red)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update-best-end-dates

(deftest best-end-date-edit-roundtrip
  (let [[p _ _] (gen-complex-struct-with-dates)
        p (reduce
            (fn [r p]
              (update-in r p
                         dissoc
                         :best-end-date :ccr :total-touch-time :total-buffer))
            p
            [[:structs 1 :routes 1]
             [:structs 1 :components 3 :structs 2 :routes 1]])
        ed (best-end-dates p)]
    (is (= (update-best-end-dates (remove-best-end-dates p) ed) p))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; best-end-date

(defspec raw-part-best-end-date
  (prop/for-all [p (gen-raw-part)]
                (is (= (:best-end-date p) (best-end-date p)))))

(deftest purchased-structure-best-end-date
  (let [d (gen-date)
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
                         :touch-time 10}]}]
    (is (nil? (best-end-date r)))))

(deftest calculated-route-best-end-date
  (let [r {:id {:type :manufactured :revision 1 :alternative "*"}
           :best-end-date (gen-date)
           :ccr nil
           :total-touch-time 10
           :total-buffer 5
           :operations [{:id 10
                         :work-center {:id "MC032"
                                       :type :internal
                                       :hours-per-day 8
                                       :potential-ccr? true}
                         :touch-time 10}]}]
    (is (= (:best-end-date r) (best-end-date r)))))

(deftest manufactured-structure-best-end-date
  (let [o {:id 10 :touch-time 10
           :work-center {:id "MC032"
                         :type :internal
                         :hours-per-day 8
                         :potential-ccr? true}}
        d (gen-date)
        s {:id {:type :manufactured :revision 1 :alternative "*"}
           :components {}
           :route-in-use 812
           :routes
           {1 {:id {:type :manufactured :revision 1 :alternative "*"}
               :best-end-date (java.util.Date.)
               :ccr nil
               :total-touch-time 10
               :total-buffer 5
               :operations [o]}
            812 {:id {:type :manufactured :revision 1 :alternative "1"}
                 :operations [o]}
            999 {:id {:type :manufactured :revision 2 :alternative "*"}
                 :best-end-date (t/minus d (t/days (rand-int 99)))
                 :ccr nil
                 :total-touch-time 10
                 :total-buffer 5
                 :operations [o]}}}]
    (is (nil? (best-end-date s)))
    (is (= d (best-end-date (update-in s [:routes 812]
                                       assoc
                                       :best-end-date d
                                       :ccr nil
                                       :total-touch-time 10
                                       :total-buffer 5))))))

(deftest structured-part-best-end-date
  (let [o {:id 10 :touch-time 10
           :work-center {:id "MC032"
                         :type :internal
                         :hours-per-day 8
                         :potential-ccr? true}}
        d1 (gen-date)
        d2 (gen-date)
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
                                     :total-touch-time 10
                                     :total-buffer 5))))))
