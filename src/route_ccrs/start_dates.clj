(ns route-ccrs.start-dates
  "Provides a single fn for determining the earliest possible start date
  of a part, structure, or routing."
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.util :refer [defmethods]]
            [route-ccrs.util.schema-dispatch :refer [get-schema]]
            [route-ccrs.schema.routes :as rs]
            [route-ccrs.schema.parts :as ps]
            [route-ccrs.best-end-dates :refer [best-end-date]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Aggregating component end dates

(defn component-end-dates [c]
  (map #(if-let [d (best-end-date %)]
          (tc/to-date-time d))
       (if (map? c)
         (vals c)
         c)))

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

(defmulti start-date
  "Returns the earliest start date of `r`, a part, structure, or route.

  If `r` is a routing then the start date is today.

  If `r` is a purchased raw part then the start date is today.

  If `r` is a structured part then the start date is the start date of
  the structure currently in use.

  If `r` is a structure then the start date is the greatest of its
  component end dates, or today if it has no components or they don't
  have end dates.

  `nil` is returned if `r` no matching method can be found for `r`."
  (fn [r] (get-schema start-date r)))

(defmethods start-date [r]
  rs/Route (t/today)
  ps/PurchasedRawPart (t/today)
  ps/StructuredPart (start-date (get-in r [:structs (:struct-in-use r)]))
  ps/Structure (or (max-component-end-date (:components r)) (t/today)))

(defmethod start-date :default [x] nil)
