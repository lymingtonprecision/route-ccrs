(ns route-ccrs.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer (env)]
            [route-ccrs.logging :as log]
            [route-ccrs.database :as db]
            [route-ccrs.parts :refer [ifs-part-store]]
            [route-ccrs.best-end-dates.ifs :refer [ifs-date-calculator]]))

(defn system
  ([] (system env))
  ([env]
   (component/system-map
     :env env
     :db (db/database)
     :part-store (ifs-part-store)
     :date-calculator (ifs-date-calculator))))

(defn start [s]
  (log/start!)
  (component/start s))

(defn stop [s]
  (log/stop!)
  (component/stop s))
