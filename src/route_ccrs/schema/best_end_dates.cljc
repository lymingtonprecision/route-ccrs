(ns route-ccrs.schema.best-end-dates
  (:require [schema.core :as s]
            [route-ccrs.schema.ids :as ids]
            [route-ccrs.schema.dates :as d]))

(def BestEndDateMap
  "A nested map from part numbers to dates and child structure and route
  IDs to dates.

  Used both as an input to and output from fns related to manipulating
  or extracting the best end dates of these records.

  Example:

  ```clojure
  {\"100102738R01\"
   {:best-end-date #inst \"2015-03-25T10:14:06.162-00:00\"
    ; structures at this level
    {:type :purchased :revision 1 :alternative \"*\"}
      {:best-end-date #inst \"2015-06-07T00:00:00-00:00\"}
    {:type :manufactured :revision 1 :alternative \"*\"}
      ; routes at this level
      {{:type :manufactured :revision 1 :alternative \"*\"}
         {:best-end-date #inst \"2015-04-15T00:00:00-00:00\"}}}}
  ```

  It's structured such that you can build up a list of the IDs of the
  record you are interested in, add `:best-end-date` to the end, and
  pass the resulting sequence of keys to `get-in` or `assoc-in`:

  ```clojure
  ; get the best end date for a part
  (get-in m [\"100102738R01\" :best-end-date])
  ; get the best end date for a structure
  (get-in m [\"100102738R01\"
             {:type :purchased :revision 1 :alternative \"*\"}])
  ; get the best end date for a routing
  (get-in m [\"100102738R01\"
             ; structure id
             {:type :manufactured :revision 2 :alternative \"*\"}
             ; routing id
             {:type :manufactured :revision 1 :alternative \"*\"}
             :best-end-date])
  ```

  (The `ids-from-part-to-loc` fn in the `part-zipper` ns is
  conspicuously useful for producing these types of sequences.)"
  {ids/PartNo
   {(s/optional-key :best-end-date) d/DateInst
    ids/ManufacturingMethod
    {(s/optional-key :best-end-date) d/DateInst
     ids/ManufacturedMethodId {:best-end-date d/DateInst}}}})
