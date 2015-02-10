(ns route-ccrs.core
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [route-ccrs.part :refer [active-parts]]
            [route-ccrs.part.process :refer [process-parts]]
            [route-ccrs.route-ccr :as ccr]))

(defmacro with-db-connection
  ^{:private true}
  [sys & body]
  `(let [~'db (:db ~sys)
         ~'c {:connection ~'db}]
     ~@body))

(defn- apply-updates! [sys updates]
  (with-db-connection sys
    (reduce
      (fn [totals route-update]
        (let [update-type (-> route-update second first)]
          (ccr/update! db route-update)
          (update-in totals [update-type] #(inc (or % 0)))))
      {}
      updates)))

(defn calculate-and-record-ccrs! [sys]
  (with-db-connection sys
    (log/info "CCR calculation started")
    (->> (active-parts {} c)
         (process-parts db)
         (apply-updates! sys)
         (log/info "calculation complete, updated:"))))
