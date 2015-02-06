(ns route-ccrs.route-ccr
  (:require [clojure.string :refer [replace] :rename {replace str-replace}]
            [clojure.data :refer [diff]]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defquery]]))

(def buffer-factor 1.5)
(def buffer-tolerance 0.041M) ; about an hour

(defquery best-end-date "route_ccrs/sql/best_end_date.sql")
(defquery best-unconstrained-end-date "route_ccrs/sql/best_unconstrained_end_date.sql")
(defquery last-known-ccr "route_ccrs/sql/last_known_ccr.sql")

(defn get-last-known-ccr [r opt]
  (last-known-ccr r (merge {:result-set-fn first} opt)))

(defquery insert-history-entry! "route_ccrs/sql/insert_history.sql")
(defquery insert-current-ccr! "route_ccrs/sql/insert_ccr.sql")
(defquery update-current-ccr! "route_ccrs/sql/update_ccr.sql")
(defquery replace-current-ccr! "route_ccrs/sql/replace_ccr.sql")

(defn op-buffer-days [o]
  (-> (* (:touch_time o) buffer-factor)
      (/ 60)
      (/ (:hours_per_day o))))

(defn sum-op-buffer-days [ops]
  (reduce (fn [s o] (+ s (op-buffer-days o))) 0 ops))

(defn on-ccr-wc? [o]
  (= (:potential_ccr o "N") "Y"))

(defn update-ccr-best-end-dates [db ccrs]
  (map #(merge % (first (best-end-date % {:connection db}))) ccrs))

(def ccr-map
  {:contract nil
   :operation_no nil
   :work_center_no nil
   :total_touch_time nil
   :pre_ccr_buffer nil
   :post_ccr_buffer nil})

(defn create-unconstrained-ccr-entry [db r]
  (let [ccr (merge ccr-map {:contract (-> r first :contract)
                            :post_ccr_buffer (sum-op-buffer-days r)
                            :total_touch_time (reduce + 0 (map :touch_time r))})]
    (->> (best-unconstrained-end-date ccr {:connection db})
         first
         (merge ccr))))

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

(defn select-current-ccr [db r]
  (let [ccr-ops (reduce-to-ccr-ops r)]
    (if (seq ccr-ops)
      (->> (update-ccr-best-end-dates db ccr-ops)
           (filter :best_end_date)
           (sort-by :best_end_date)
           first)
      (create-unconstrained-ccr-entry db r))))

(defn- split-keys
  ([map] map)
  ([map key]
   [(dissoc map key) (select-keys map [key])])
  ([map key & ks]
   (let [ks (conj ks key)]
     [(apply dissoc map ks)
      (select-keys map ks)])))

(defn- outside-tolerance? [k x y]
  (let [x (get x k)
        y (get y k)]
    (if (and x y)
      (-> (- x y) bigdec .abs (> buffer-tolerance))
      true)))

(defn diff-ccrs [x y]
  (reduce
    (fn [[n o s] k]
      (if (and (contains? n k)
               (outside-tolerance? k n o))
        [n o s]
        [(dissoc n k) (dissoc o k) (merge s (select-keys o [k]))]))
    (diff x y)
    [:total_touch_time :pre_ccr_buffer :post_ccr_buffer]))

(defn ccr-entry-updates [id current-ccr new-ccr]
  (let [[old-values new-values no-change] (diff-ccrs current-ccr new-ccr)
        updated-ccr (merge id no-change new-values)
        ts (java.sql.Timestamp. (.getTime (java.util.Date.)))]
    (if (seq new-values)
      [(assoc updated-ccr :calculated_at ts)
       (if (or (contains? new-values :work_center_no)
               (nil? (:best_end_date current-ccr)))
         [(if (seq current-ccr) :replace :insert) (assoc updated-ccr :ccr_as_of ts)]
         [:update updated-ccr])])))

(defn update! [db [hist [t r]]]
  (let [c {:connection db}]
    (insert-history-entry! hist c)
    (cond
      (= :replace t) (replace-current-ccr! r c)
      (= :update t) (update-current-ccr! r c)
      (= :insert t) (insert-current-ccr! r c))))
