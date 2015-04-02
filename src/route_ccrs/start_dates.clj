(ns route-ccrs.start-dates
  "Provides a single fn for determining the earliest possible start date
  of a part, structure, or routing."
  (:require [schema.core :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [route-ccrs.util :refer [defmethods]]
            [route-ccrs.util.schema-dispatch :refer [get-schema]]
            [route-ccrs.schema.dates :refer [Date]]
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
;; Private multi method definition

(defmulti ^:private -start-date
  (fn [r default] (get-schema -start-date r)))

(defmethods -start-date [r default]
  rs/Route default
  ps/PurchasedRawPart default
  ps/StructuredPart (-start-date
                      (get-in r [:structs (:struct-in-use r)])
                      default)
  ps/Structure (or (max-component-end-date (:components r)) default))

(defmethod -start-date :default [_ _] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn start-date :- (s/maybe Date)
  "Returns the earliest start date of `r`, a part, structure, or route.
  In cases where this cannot be derived from the end date of child
  elements (e.g. for purchased raw parts) then the `default` is used
  (which defaults to today.)

  If `r` is a routing then the start date is the `default`.

  If `r` is a purchased raw part then the start date is the `default`.

  If `r` is a structured part then the start date is the start date of
  the structure currently in use.

  If `r` is a structure then the start date is the greatest of its
  component end dates, or the `default` if it has no components or they
  don't have end dates.

  `nil` is returned if `r` no matching method can be found for `r`."
  ([r] (start-date r (t/today)))
  ([r default :- Date] (-start-date r default)))
