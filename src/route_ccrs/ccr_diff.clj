(ns route-ccrs.ccr-diff
  (:require [clojure.data :refer [diff]]))

(def buffer-tolerance 0.041M) ; about an hour

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
