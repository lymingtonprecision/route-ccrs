(ns route-ccrs.part-store-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.test-util :as tu]
            [yesql.core :refer [defquery]]

            [clj-time.core :as t]
            [clj-time.coerce :as tc]

            [route-ccrs.part-zipper :as pz]
            [route-ccrs.part-store :refer :all]))

(def ^:dynamic *default-db-test-count* 10)

(def part-store (atom {}))

(defn create-part-store [test-fn]
  (tu/start-test-system)
  (reset! part-store (map->IFSPartStore @tu/test-system))
  (test-fn))

(use-fixtures :once create-part-store)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Deserialization tests

(deftest deserialize-part-test
  (is (= {:type :raw :lead-time 10 :best-end-date nil}
         (deserialize-part
          {:type "raw" :lead_time 10.0134 :best_end_date nil}))))

(deftest deserialize-mm-id-test
  (is (= {:type :manufactured :revision 1 :alternative "*"}
         (deserialize-mm-id
          {:type "manufactured" :revision "1" :alternative "*"}))))

(deftest deserialize-route-operation-test
  (is (= {:route {:type :manufactured :revision 1 :alternative "*"
                  :description "Default Method of Manufacture"}
          :id 10
          :description "CNC Lathe"
          :touch-time 25
          :work-center {:id "MC032"
                        :description "Mazak Integrex 200Y"
                        :type :internal
                        :hours-per-day 8.1
                        :potential-ccr? true}}
         (deserialize-route-operation
          {:route__type "manufactured"
           :route__revision "1"
           :route__alternative "*"
           :route__description "Default Method of Manufacture"
           :id 10M
           :description "CNC Lathe"
           :touch_time 25.001
           :work_center "MC032"
           :work_center_description "Mazak Integrex 200Y"
           :type "internal"
           :hours_per_day 8.1
           :potential_ccr "Y"}))))

(deftest deserialize-structure-test
  (let [d (tc/to-sql-date (t/today))
        s {:type "manufactured"
           :revision "1"
           :alternative "*"
           :description "Default Method of Manufacture"
           :lead_time 10
           :best_end_date d}]
    (is (= {:id {:type :manufactured :revision 1 :alternative "*"}
            :description "Default Method of Manufacture"}
           (deserialize-structure s)))
    (is (= {:id {:type :purchased :revision 1 :alternative "*"}
            :description "Default Method of Manufacture"
            :lead-time 10
            :best-end-date (tc/to-local-date d)}
           (deserialize-structure (assoc s :type "purchased"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mm->sql-id-params

(deftest mm->sql-id-params-test
  (is (= {:bom_type "manufactured" :revision "1" :alternative "*"}
         (mm->sql-id-params
          {:id {:type :manufactured :revision 1 :alternative "*"}}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol implementation tests, against the database

(defspec ^:db active-parts-test *default-db-test-count*
  (prop/for-all
   [n (gen/such-that pos? gen/pos-int)]
   (is (= n (count (take n (active-parts @part-store)))))))

(defquery -raw-parts "queries/raw_parts.sql")
(defquery -non-existant-part-no "queries/non_existant_part_no.sql")
(defquery -valid-full-parts "queries/active_and_valid_full_bom_parts.sql")
(defquery -invalid-full-parts "queries/invalid_full_bom_parts.sql")

(deftest ^:db get-non-existant-part
  (let [pn (-non-existant-part-no {} {:connection (:db @tu/test-system)
                                      :result-set-fn first})]
    (is (= [:error {:invalid-part (:id pn)}]
           (get-part @part-store pn)))))

(defspec ^:db get-raw-parts-test *default-db-test-count*
  (prop/for-all
   [p (gen/elements (-raw-parts {} {:connection (:db @tu/test-system)}))
    r gen/boolean]
   (is (= (:id p) (-> (get-part @part-store p r) second :id)))))

(defspec ^:db get-invalid-full-parts-test *default-db-test-count*
  (prop/for-all
    [pn (gen/elements (-invalid-full-parts {}
                                           {:connection (:db @tu/test-system)
                                            :row-fn :id}))]
    (let [r (get-part @part-store {:id pn} true)]
      (is (= :error (first r)))
      (is (= pn (-> r second :id)))
      (is (not-empty (-> r second (dissoc :id)))))))

(defspec ^:db get-full-parts-test *default-db-test-count*
  (prop/for-all
    [p (gen/elements (-valid-full-parts {:min_depth 0}
                                        {:connection (:db @tu/test-system)}))
     r gen/boolean]
    (is (= (:id p) (-> (get-part @part-store p r) second :id)))))

(defspec ^:db raw-parts-have-descriptions *default-db-test-count*
  (prop/for-all
    [p (gen/elements (-raw-parts {} {:connection (:db @tu/test-system)}))]
    (is (not (nil? (-> (get-part @part-store p) second :description))))))

(defspec ^:db full-parts-have-descriptive-fields *default-db-test-count*
  (prop/for-all
    [p (gen/elements (-valid-full-parts {:min_depth 0}
                                        {:connection (:db @tu/test-system)}))]
    (let [p (second (get-part @part-store p false))]
      (is (not (nil? (:description p))))
      (is (contains? p :customer-part))
      (is (contains? p :issue)))))

(defn reduce-children [f i s]
  (loop [r i loc (-> s pz/down pz/rightmost)]
    (if (nil? loc)
      r
      (recur (f r loc) (pz/left loc)))))

(defn has-child-components? [p]
  (if (and (pz/node-val p) (pz/branch? p))
    (some seq (map #(-> % vals first :components) (pz/children p)))
    false))

(defn has-no-second-level-children? [p]
  (let [z (pz/part-zipper p)
        slc (reduce-children
             (fn [r struct]
               (conj
                r
                (reduce-children
                 (fn [r component]
                   (or r (has-child-components? component)))
                 false
                 (pz/down struct))))
             []
             z)]
    (if (some identity slc)
      false
      true)))

(defspec ^:db get-non-recursive-parts-test *default-db-test-count*
  (prop/for-all
   [p (gen/elements (-valid-full-parts {:min_depth 2}
                                 {:connection (:db @tu/test-system)}))]
   (is (= true
          (has-no-second-level-children?
            (second (get-part @part-store p false)))))))
