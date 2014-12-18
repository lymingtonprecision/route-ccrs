(ns route-ccrs.main
  (:require [route-ccrs.core :refer [calculate-and-record-ccrs!]]
            [route-ccrs.system :as sys])
  (:gen-class))

(defn -main [& args]
  (let [s (sys/start (sys/system))]
    (calculate-and-record-ccrs! s)))
