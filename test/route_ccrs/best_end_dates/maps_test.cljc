(ns route-ccrs.best-end-dates.maps-test
  #?(:cljs (:require-macros [cljs.test :refer [use-fixtures deftest is]]))
  (:require #?(:clj  [clojure.test :refer [use-fixtures deftest is]]
               :cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [schema.test]
            #?(:clj  [clj-time.core :as t]
               :cljs [cljs-time.core :as t])
            [route-ccrs.generators.raw-part :refer [gen-raw-part]]
            [route-ccrs.best-end-dates :refer [remove-best-end-dates]]
            [route-ccrs.best-end-dates.maps
             :refer [part->end-date-map update-best-end-dates-from-map]]))

(def ^:dynamic *max-multilevel-depth* 5)

(use-fixtures :once schema.test/validate-schemas)

(defn rand-date [] (t/plus (t/today) (t/days (rand-int 100))))

(defn gen-complex-struct-with-dates []
  (let [d (repeatedly 5 rand-date)
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
                   :ccr-queue 0
                   :total-touch-time 50
                   :total-buffer 5
                   :operations [{:id 10
                                 :work-center {:id "MC032"
                                               :type :internal
                                               :hours-per-day 8
                                               :potential-ccr? true}
                                 :touch-time 10
                                 :buffer 5}]}}
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
                           :ccr-queue 0
                           :total-touch-time 50
                           :total-buffer 5
                           :operations [{:id 10
                                         :work-center {:id "OW003"
                                                       :type :external
                                                       :hours-per-day 24
                                                       :potential-ccr? false}
                                         :touch-time 240
                                         :buffer 0}]}}
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
;; part->end-date-map

(defspec end-date-map-for-raw-part
  (prop/for-all [p (gen-raw-part)]
                (let [ex (if (:best-end-date p)
                           {(:id p) {:best-end-date (:best-end-date p)}}
                           {})]
                  (is (= ex (part->end-date-map p))))))

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
       (map part->end-date-map)
       (apply merge)))

(defn dissoc-nils [m]
  (reduce
   (fn [r [k v]]
     (if (nil? v)
       r
       (assoc r k v)))
   {}
   m))

(deftest end-date-map-for-structured-part
  (let [[s d ex] (gen-complex-struct-with-dates)]
    (is (= ex (part->end-date-map s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update-best-end-dates-from-map

(deftest best-end-date-edit-roundtrip
  (let [[p _ _] (gen-complex-struct-with-dates)
        p (reduce
           (fn [r p]
             (update-in
               r p dissoc
               :best-end-date :ccr :ccr-queue :total-touch-time :total-buffer))
           p
           [[:structs 1 :routes 1]
            [:structs 1 :components 3 :structs 2 :routes 1]])
        ed (part->end-date-map p)]
    (is (= p
           (update-best-end-dates-from-map
            (remove-best-end-dates p)
            ed)))))
