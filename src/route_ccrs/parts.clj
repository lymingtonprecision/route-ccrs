(ns route-ccrs.parts
  (:require [yesql.core :refer [defquery]]
            [route-ccrs.processing :refer :all]
            [route-ccrs.routes :refer :all]))

(defquery active-parts "route_ccrs/sql/active_parts.sql")

(defn process-part [db part]
  (let [routes (deferred-routes db part)]
    (process-routes db @routes)))

(defn process-parts [db parts]
  (parallel-process (partial process-part db) parts 10 true))
