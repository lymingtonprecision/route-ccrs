(ns route-ccrs.core
  (:require [clojure.tools.logging :as log]
            [route-ccrs.active-routes :as ar]
            [route-ccrs.route-ccr :as ccr]))

(defmacro with-db-connection
  ^{:private true}
  [sys & body]
  `(let [~'db (:db ~sys)
         ~'c {:connection ~'db}]
     ~@body))

(defn- route-update [sys [route-id operations]]
  (with-db-connection sys
    (ccr/ccr-entry-updates
      route-id
      (ccr/get-last-known-ccr route-id c)
      (ccr/select-current-ccr db operations))))

(defn- reduce-to-updates [sys routes]
  (reduce
    (fn [u route]
      (let [ru (route-update sys route)]
        (if (seq ru)
          (conj u ru)
          u)))
    []
    routes))

(defn- apply-updates! [sys updates]
  (with-db-connection sys
    (reduce
      (fn [totals route-update]
        (let [update-type (-> route-update second first)]
          (ccr/update! db route-update)
          (update-in totals [update-type] #(inc (or % 0)))))
      {}
      updates)))

(defn calculate-and-record-ccrs!  [sys]
  (with-db-connection sys
    (log/info "CCR calculation started")
    (->> (into [] ar/transduce-routes (ar/active-routes {} c))
         (reduce-to-updates sys)
         (apply-updates! sys)
         (log/info "calculation complete, updated:"))))
