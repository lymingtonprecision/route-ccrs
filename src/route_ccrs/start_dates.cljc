(ns route-ccrs.start-dates
  "Provides a single fn for determining the earliest possible start date
  of a part, structure, or routing."
  (:require [schema.core :as s]
            #?@(:clj  [[clj-time.core :as t]
                       [clj-time.coerce :as tc]]
                :cljs [[cljs-time.core :as t]
                       [cljs-time.coerce :as tc]
                       [cljs-time.extend]])
            [route-ccrs.util.schema-dispatch :refer [matching-schema]]
            [route-ccrs.schema.dates :refer [DateInst]]
            [route-ccrs.schema.routes :as rs]
            [route-ccrs.schema.parts :as ps :refer [sourced?]]
            [route-ccrs.best-end-dates :refer [best-end-date]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Aggregating component end dates

(defn component-end-dates [c]
  (->> (if (map? c) (vals c) c)
       (map #(if-let [d (best-end-date %)] (tc/to-date-time d)))
       (filter (complement nil?))))

(defn max-component-end-date
  "Returns the maximum best end date from the components `c`, or `nil`
  if no components have an end date."
  [c]
  (let [ed (component-end-dates c)]
    (if (seq ed)
      (t/latest ed)
      nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn start-date :- (s/maybe DateInst)
  "Returns the earliest start date of `r`, a part, structure, or route.
  In cases where this cannot be derived from the end date of child
  elements (e.g. for purchased raw parts) then the `default` is used
  (which defaults to today.)

  If `r` is a routing then the start date is the `default`.

  If `r` is a purchased raw part then the start date is the `default`.

  If `r` is a non-sourced structured part then the start date is the
  start date of the structure currently in use.

  If `r` is a _sourced_ structured part then the start date is the
  `default`.

  If `r` is a structure then the start date is the greatest of its
  component end dates, or the `default` if it has no components or they
  don't have end dates.

  `nil` is returned if `r` no matching method can be found for `r`."
  ([r] (start-date r (t/today)))
  ([r default :- DateInst]
   (cond
     (matching-schema r rs/Route) default
     (matching-schema r ps/PurchasedRawPart) default
     (matching-schema r ps/StructuredPart)
     (if (sourced? r)
       default
       (start-date (get-in r [:structs (:struct-in-use r)]) default))
     (matching-schema r ps/Structure)
     (or (max-component-end-date (:components r)) default)
     :else nil)))
