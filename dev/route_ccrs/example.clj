(ns route-ccrs.example
  (:require [clojure.pprint :refer [pprint]]
            [hikari-cp.core :as hk]
            [com.stuartsierra.component :as component]
            [route-ccrs.part-store :as ps]
            [route-ccrs.best-end-dates :refer [best-end-date]]
            [route-ccrs.best-end-dates.calculator :as dc]
            [route-ccrs.best-end-dates.update :refer :all]))

(defrecord IFS [host instance user password]
  component/Lifecycle
  (start [this]
    (let [o (merge hk/default-datasource-options
                   {:adapter "oracle"
                    :driver-type "thin"
                    :server-name host
                    :port-number 1521
                    :database-name instance
                    :username user
                    :password password
                    :minimum-idle 1
                    :maximum-pool-size 10})
          ds (hk/make-datasource o)]
      (assoc this :options o :datasource ds)))
  (stop [this]
    (if-let [ds (:datasource this)]
      (hk/close-datasource ds))
    (dissoc this :datasource)))

(defn system [host instance user password]
  (let [s (component/system-map
           :db (->IFS host instance user password)
           :part-store (ps/ifs-part-store)
           :date-calculator (dc/ifs-date-calculator))]
    (component/start s)))

(let [sys (system "database-server" "database-instance" "user" "password")
      pid (rand-nth (ps/active-parts (:part-store sys)))
      [r p] (ps/get-part (:part-store sys) pid)
      up (if (= :ok r)
           (update-all-best-end-dates-under-part p (:date-calculator sys)))
      _ (component/stop sys)]
  (if (= :ok r)
    (do
      (pprint (str (:id pid) "'s best end date is " (best-end-date up)))
      (pprint "The full structure is:")
      (pprint up))
    (do
      (pprint (str "Error processing part " (:id pid) ":"))
      (pprint p))))
