(ns route-ccrs.parts
  (:require [clojure.core.async :as async]
            [yesql.core :refer [defquery]]
            [route-ccrs.processing :refer :all]
            [route-ccrs.routes :as route]
            [route-ccrs.structs :as struct]))

(defquery active-parts "route_ccrs/sql/active_parts.sql")

(defn process-part
  "Calculates the best end dates/CCRs for the active structures and routings
  of the given part.

  Returns a tuple of `[best-end-dates route-ccr-updates structure-ccr-updates]`
  where `best-end-dates` is a map of structure IDs to records that contain, at
  least, the calculated best end date when using the default routing, and the
  two `-updates` elements are collections of database update records reflecting
  any changes from the current state.

  Takes, as arguments:

  * `db` a JDBC database specification
  * `part` a part record
  * `best-assembly-dates`, optional, a map of structure IDs to records that
    contain their best end dates. Used as a lookup for the availability dates
    of components (the `best-end-dates` return value of this `fn` matches this
    format, to enable recursive calls and processing BOMs bottom up)"
  ([db part] (process-part db part {}))
  ([db part best-assembly-dates]
   (let [routes (route/deferred-routes db part)
         structs (struct/deferred-structs db part)
         route-updates (promise)
         _ (async/go
             (let [u (route/process-routes db @routes)]
               (deliver route-updates u)))
         best-end-dates (promise)
         struct-updates (promise)
         _ (async/go
             (let [[b u] (struct/process-structs
                           db @structs @routes best-assembly-dates)]
               (deliver best-end-dates b)
               (deliver struct-updates u)))]
     [@best-end-dates @route-updates @struct-updates])))

(defn process-parts
  "Processes a collection of parts in parallel, passing each to `process-part`
  and returning a concatenation of the results."
  ([db parts] (process-parts db parts {}))
  ([db parts best-assembly-dates]
   (reduce
     (fn [[bed ru su] [b r s]]
       [(merge bed b) (concat ru r) (concat su s)])
     [{} [] []]
     (parallel-process #(process-part db % best-assembly-dates) parts))))

(defn process-part-batches
  "Processes a collection of collections of parts, passing each batch of parts
  to `process-parts` _with the `best-end-dates` of all preceding batches_ and
  returning a concatenation of the results."
  [db batches]
  (reduce
    (fn [[bed ru su] parts]
      (let [[b r s] (process-parts db parts bed)]
        [(merge bed b)
         (concat ru r)
         (concat su s)]))
    [{} [] []]
    batches))
