(ns route-ccrs.best-end-dates
  "Functions related to the extraction and manipulation of best end
  dates within part records (and their constituent structures, routings,
  and components.)

  This namespace includes only those functions that operate on the dates
  present in the part records themselves. See the `.calculation`
  sub-namespace for functions that deal with calculating dates from
  the attributes of the part records.

  There are two primary functions within this namespace:

  * `best-end-date` which returns the explicit or implicit end date of
    a part, structure, or routing.
  * `remove-end-dates` which returns a copy of a part structure with
    all of the best end dates removed, from every record at every level.

  These two functions should cover at least 80% of your requirements in
  dealing with best end dates (outside of calculating them.) The other
  public functions should cover everything else you want to do that
  seems reasonable to be doing."
  (:require [schema.core :as s]
            [route-ccrs.schema.ids :as ids]
            [route-ccrs.schema.dates :refer [Date]]
            [route-ccrs.schema.parts :as ps]
            [route-ccrs.schema.routes :as rs]

            [route-ccrs.util :refer [defmethods]]
            [route-ccrs.util.schema-dispatch :refer [get-schema]]

            [clojure.zip :as zip]
            [route-ccrs.part-zipper :as pz :refer [part-zipper]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

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

  (The `path-from-part-to-loc` fn in the `part-zipper` ns is
  conspicuously useful for producing these types of path vectors.)"
  {ids/PartNo
   {(s/optional-key :best-end-date) Date
    ids/ManufacturingMethod
    {(s/optional-key :best-end-date) Date
     ids/ManufacturedMethodId {:best-end-date Date}}}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; End date manipulation

(defn remove-best-end-date
  "Given a part-zipper location returns the same location modified so
  that its value doesn't contain a best end date."
  [n]
  (if (nil? (s/check rs/CalculatedRoute (pz/node-val n)))
    (pz/edit-val n #(apply dissoc % (keys rs/RouteCalculationResults)))
    (pz/edit-val n assoc :best-end-date nil)))

(defn update-best-end-date
  "Given a part-zipper location and best end date map returns the same
  location modified to include the new best end date, if there's a
  matching entry in the provided best end dates.

  Will remove the best end date from the node if there is a `nil` entry
  in the best end date map."
  [n ed]
  (let [p (conj (pz/path-from-part-to-loc n) :best-end-date)
        d (get-in ed p ::no-date-given)]
    (if (= ::no-date-given d)
      n
      (if (nil? d)
        (remove-best-end-date n)
        (pz/edit-val n assoc :best-end-date d)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defmulti best-end-date
  "Returns the best end date of `x`, where `x` is a record matching a
  schema for which a best end date can be explicitly set or implied
  from child records.

  The only implied end dates are:

  * That of a manufactured structure from its `route-in-use`
  * That of a structured part from its `struct-in-use`

  In all other cases this fn merely returns the current value of the
  `:best-end-date` and in those cases only 'bubbles up' the date from
  a lower level. This fn does **not** attempt any calculation, even when
  it might be simple (like working forwards by a lead time or for a
  fictitiously sourced part.)

  Throws an IllegalArgumentException if `x` does not represent an
  appropriate record."
  (fn [x] (get-schema best-end-date x)))

(defmethods best-end-date [x]
  rs/CalculatedRoute (:best-end-date x)
  rs/UncalculatedRoute nil
  ps/PurchasedStructure (:best-end-date x)
  ps/PurchasedRawPart (:best-end-date x)
  ps/ManufacturedStructure (best-end-date
                             (get-in x [:routes (:route-in-use x)]))
  ps/StructuredPart (if-let [d (:best-end-date x)]
                      d
                      (best-end-date (get-in x [:structs (:struct-in-use x)]))))

(s/defn best-end-dates :- BestEndDateMap
  "Returns a `BestEndDateMap` of the explicitly defined best end dates
  from `part` and all its child records (structures, routes, component
  parts.) If no best end dates are found an empty map is returned.

  Why only the _explicitly defined_ end dates? To avoid confusion.
  Including implied end dates could result in them being inadvertently
  set explicitly via `update-best-end-dates` and then never adjusted
  when lower level records change.

  (For details of which dates are explicit or implicit refer to
  `best-end-date`.)"
  [part :- ps/Part]
  (loop [bed {} loc (part-zipper part)]
    (if (zip/end? loc)
      bed
      (let [bed (if-let [ed (:best-end-date (pz/node-val loc))]
                  (assoc-in bed
                            (conj (pz/path-from-part-to-loc loc) :best-end-date)
                            ed)
                  bed)]
        (recur bed (zip/next loc))))))

(s/defn remove-best-end-dates :- ps/Part
  "Returns a copy of `part` with all current `best-end-dates` values
  removed (or set to `nil`, as appropriate.)"
  [part :- ps/Part]
  (loop [loc (part-zipper part)]
    (if (zip/end? loc)
      (pz/root-part loc)
      (let [n (if (:best-end-date (pz/node-val loc))
                (remove-best-end-date loc)
                loc)]
        (recur (zip/next n))))))

(s/defn update-best-end-dates :- ps/Part
  "Returns a copy of `part` with the best end dates of appropriate child
  entries updated from the `BestEndDateMap` `best-end-dates`."
  [part :- ps/Part, best-end-dates :- BestEndDateMap]
  (loop [loc (part-zipper part)]
    (if (zip/end? loc)
      (pz/root-part loc)
      (recur (zip/next (update-best-end-date loc best-end-dates))))))
