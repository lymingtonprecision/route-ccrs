(ns route-ccrs.ccr
  (:require [clojure.string :as str]
            [yesql.core :refer [defquery]]
            [route-ccrs.ccr-diff :refer [diff-ccrs]]))

(def buffer-factor 1.5)

(defn- get-first-result [q] (fn [p o] (q p (merge {:result-set-fn first} o))))

(defquery last-known-route-ccr "route_ccrs/sql/last_known_ccr.sql")
(def get-last-known-route-ccr (get-first-result last-known-route-ccr))

(defquery last-known-struct-ccr "route_ccrs/sql/last_known_struct_ccr.sql")
(def get-last-known-struct-ccr (get-first-result last-known-struct-ccr))

(defquery best-end-date "route_ccrs/sql/best_end_date.sql")
(defquery best-unconstrained-end-date "route_ccrs/sql/best_unconstrained_end_date.sql")

(defn- work-center-type [o]
  (if-let [t (:work_center_type o)]
    (if (keyword? t)
      t
      (-> t str str/lower-case keyword))))

(defn op-buffer-days [o]
  (-> (if (= (work-center-type o) :external)
        (:touch_time o)
        (* (:touch_time o) buffer-factor))
      (/ 60.0)
      (/ (:hours_per_day o))))

(defn sum-op-buffer-days [ops]
  (reduce (fn [s o] (+ s (op-buffer-days o))) 0 ops))

(defn on-ccr-wc? [o]
  (= (:potential_ccr o "N") "Y"))

(defn update-ccr-best-end-dates
  ([ccrs db] (update-ccr-best-end-dates nil ccrs db))
  ([start-date ccrs db]
   (map
     #(merge % (first (best-end-date (assoc % :start_date start-date) db)))
     ccrs)))

(def ccr-map
  {:contract nil
   :operation_no nil
   :work_center_no nil
   :total_touch_time nil
   :pre_ccr_buffer nil
   :post_ccr_buffer nil})

(defn create-unconstrained-ccr-entry
  ([r db] (create-unconstrained-ccr-entry nil r db))
  ([start-date r db]
   (let [ccr (merge ccr-map {:contract (-> r first :contract)
                             :post_ccr_buffer (sum-op-buffer-days r)
                             :total_touch_time (reduce + 0 (map :touch_time r))})]
     (->> (best-unconstrained-end-date (assoc ccr :start_date start-date) db)
          first
          (merge ccr)))))

(defn add-op-to-ccr-list [ccrs o pre post]
  (let [wc (select-keys o [:contract :work_center_no])
        ccr (get ccrs wc)]
    (assoc ccrs wc (if ccr
                     (-> ccr
                         (update :total_touch_time + (:touch_time o))
                         (update :post_ccr_buffer
                                 (fn [o n] (max 0 (- o n)))
                                 (op-buffer-days o)))
                     (merge wc {:operation_no (:operation_no o)
                                :total_touch_time (:touch_time o)
                                :pre_ccr_buffer pre
                                :post_ccr_buffer post})))))

(defn reduce-to-ccr-ops [r]
  (-> (reduce
        (fn [[pre-op-buffer post-op-buffer ccrs] o]
          (let [ob (op-buffer-days o)
                pob (- post-op-buffer ob)
                r (if (on-ccr-wc? o)
                    (add-op-to-ccr-list ccrs o pre-op-buffer pob)
                    ccrs)]
            [(+ pre-op-buffer ob) pob r]))
        [0 (sum-op-buffer-days r) {}]
        r)
      last
      vals))

(defn select-current-ccr
  ([r db] (select-current-ccr nil r db))
  ([start-date r db]
   (let [ccr-ops (reduce-to-ccr-ops r)]
     (if (seq ccr-ops)
       (->> (update-ccr-best-end-dates start-date ccr-ops db)
            (filter :best_end_date)
            (sort-by :best_end_date)
            first)
       (create-unconstrained-ccr-entry start-date r db)))))

(defn ccr-entry-updates [id current-ccr new-ccr]
  (let [[old-values new-values no-change] (diff-ccrs current-ccr new-ccr)
        updated-ccr (merge id no-change new-values)
        ts (java.sql.Timestamp. (.getTime (java.util.Date.)))
        t (if (or (contains? new-values :work_center_no)
                  (nil? (:best_end_date current-ccr)))
            (if (seq current-ccr) :replace :insert)
            :update)]
    (if (seq new-values)
      [t (assoc updated-ccr :calculated_at ts)])))
