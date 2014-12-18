(ns route-ccrs.core
  (:require [clojure.tools.logging :as log]
            [route-ccrs.active-routes :as ar]
            [route-ccrs.route-ccr :as ccr]))

(defn calculate-and-record-ccrs!  [sys]
  (let [c {:connection ( (:db sys))}]
    (log/info "CCR calculation started")
    (->> (into [] ar/transduce-routes (ar/active-routes {} c))
         (map
           (fn [[r o]]
             (ccr/ccr-entry-updates
               r
               (ccr/get-last-known-ccr r c)
               (ccr/select-current-ccr (:db sys) o))))
         (filter seq)
         (reduce (fn [c u] (ccr/update! (:db sys) u) (+ c 1)) 0)
         (log/info "calculation complete, updated:"))))
