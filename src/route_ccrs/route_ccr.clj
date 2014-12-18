(ns route-ccrs.route-ccr
  (:require [clojure.string :refer [replace] :rename {replace str-replace}]
            [clojure.data :refer [diff]]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defquery]]))

(def buffer-factor 1.5)

(defquery best-end-date "route_ccrs/sql/best_end_date.sql")
(defquery get-last-known-ccr "route_ccrs/sql/last_known_ccr.sql")

(defquery insert-history-entry! "route_ccrs/sql/insert_history.sql")
(defquery insert-current-ccr! "route_ccrs/sql/insert_ccr.sql")
(defquery update-current-ccr! "route_ccrs/sql/update_ccr.sql")
(defquery replace-current-ccr! "route_ccrs/sql/replace_ccr.sql")

(defn op-buffer [o]
  (* (:touch_time o) buffer-factor))

(defn sum-op-buffers [ops]
  (reduce (fn [s o] (+ s (op-buffer o))) 0 ops))

(defn on-ccr-wc? [o]
  (= (:potential_ccr o "N") "Y"))

(defn update-ccr-best-end-dates [db ccrs]
  (map #(merge % (-> (best-end-date % {:connection db}) first)) ccrs))

(defn add-op-to-ccr-list [ccrs o pre post]
  (let [wc (select-keys o [:contract :work_center_no])
        ccr (get ccrs wc)]
    (assoc ccrs wc (if ccr
                     (-> ccr
                         (update :total_touch_time + (:touch_time o))
                         (update :post_ccr_buffer
                                 (fn [o n] (max 0 (- o n)))
                                 (op-buffer o)))
                     (merge wc {:operation_no (:operation_no o)
                                :total_touch_time (:touch_time o)
                                :pre_ccr_buffer pre
                                :post_ccr_buffer post})))))

(defn reduce-to-ccr-ops [r]
  (-> (reduce
        (fn [[pre-op-buffer post-op-buffer ccrs] o]
          (let [ob (op-buffer o)
                pob (- post-op-buffer ob)
                r (if (on-ccr-wc? o)
                    (add-op-to-ccr-list ccrs o pre-op-buffer pob)
                    ccrs)]
            [(+ pre-op-buffer ob) pob r]))
        [0 (sum-op-buffers r) {}]
        r)
      last
      vals))

(defn select-current-ccr [db r]
  (->> (reduce-to-ccr-ops r)
       (update-ccr-best-end-dates db)
       (filter :best_end_date)
       (sort-by :best_end_date)
       first))

(defn ccr-entry-updates [id current-ccr new-ccr]
  (let [[old-values new-values no-change] (diff current-ccr new-ccr)
        updated-ccr (merge id no-change new-values)
        ts (java.sql.Date. (.getTime (java.util.Date.)))]
    (if (seq new-values)
      [(assoc updated-ccr :calculated_at ts)
       (if (:work_center_no new-values)
         [(if (seq current-ccr) :replace :insert) (assoc updated-ccr :ccr_as_of ts)]
         [:update updated-ccr])])))

(defn update! [db [hist [t r]]]
  (let [c {:connection db}]
    (insert-history-entry! hist c)
    (cond
      (= :replace t) (replace-current-ccr! r c)
      (= :update t) (update-current-ccr! r c)
      (= :insert t) (insert-current-ccr! r c))))
