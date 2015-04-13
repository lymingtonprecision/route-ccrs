(ns route-ccrs.main
  (:require [route-ccrs.system :as sys])
  (:gen-class))

(defn -main [& args]
  (let [s (sys/start (sys/system))]
    (println "Batch processing unavailable due to refactoring")))
