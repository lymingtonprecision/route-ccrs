(ns route-ccrs.core
  (:require [clojure.tools.logging :as log]
            [route-ccrs.parts :refer :all]
            [route-ccrs.routes :as route]
            [route-ccrs.structs :as struct]))

(defmacro with-db-connection
  ^{:private true}
  [sys & body]
  `(let [~'db (:db ~sys)
         ~'c {:connection ~'db}]
     ~@body))

(defn- apply-and-total-updates! [sys update-fn updates]
  (with-db-connection sys
    (reduce
      (fn [totals u]
        (let [update-type (first u)]
          (update-fn db u)
          (update-in totals [update-type] #(inc (or % 0)))))
      {}
      updates)))

(defn- apply-updates! [sys route-updates struct-updates]
  {:routes (apply-and-total-updates! sys route/update! route-updates)
   :assemblies (apply-and-total-updates! sys struct/update! struct-updates)})

(defn calculate-and-record-ccrs! [sys]
  (with-db-connection sys
    (log/info "CCR calculation started")
    (->> (active-parts {} c)
         (partition-by :lowest_level)
         (sort-by #(-> % first :lowest_level))
         (process-part-batches db)
         rest ; drop the best end dates
         (apply apply-updates! sys)
         (log/info "calculation complete, updated:"))))
