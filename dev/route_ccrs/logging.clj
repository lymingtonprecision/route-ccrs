(ns route-ccrs.logging
  (:require [com.stuartsierra.component :as component]
            [org.spootnik.logconfig :refer (start-logging!)]))

(defn start! []
  (start-logging! {:level "info" :console true}))

(defn stop! []
  (start-logging! {:level "off" :console false}))
