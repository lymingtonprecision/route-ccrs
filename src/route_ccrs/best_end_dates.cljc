(ns route-ccrs.best-end-dates
  "Provides the most basic level of interaction with best end dates:
  reading and removing them from records.

  Exposes two functions:

  * `best-end-date` which, given a part, structure, or routing will
    return the current best end date of that record.

  * `remove-best-end-dates` which, given a part, returns a copy with
    all of the best end dates, at every level, removed. Useful for
    starting from a blank slate.

  Please see the sub-namespaces for functions interacting with the end
  dates within records in different ways."
  (:require [schema.core :as s]
            [route-ccrs.schema.dates :as ds]
            [route-ccrs.schema.parts :as ps]
            [route-ccrs.schema.routes :as rs]
            [route-ccrs.util.schema-dispatch :refer [matching-schema]]
            [route-ccrs.part-zipper :as pz :refer [part-zipper]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Cross platform helpers

(defn throw-invalid-end-date-record [x]
  (let [msg (str "don't know how to extract an end date from " x)]
    #?(:clj  (throw (IllegalArgumentException. msg))
       :cljs (throw (js/Error. msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private node update methods

(defn remove-best-end-date
  "Given a part-zipper location returns the same location modified so
  that its value doesn't contain a best end date."
  [n]
  (if (nil? (s/check rs/CalculatedRoute (pz/node-val n)))
    (pz/edit-val n #(apply dissoc % (keys rs/RouteCalculationResults)))
    (pz/edit-val n assoc :best-end-date nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn best-end-date :- (s/maybe ds/DateInst) [x]
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
  (cond
    (matching-schema x rs/UncalculatedRoute) nil
    (matching-schema x rs/CalculatedRoute) (:best-end-date x)
    (matching-schema x ps/PurchasedRawPart) (:best-end-date x)
    (matching-schema x ps/PurchasedStructure) (:best-end-date x)
    (matching-schema x ps/ManufacturedStructure)
    (best-end-date
      (get-in x [:routes (:route-in-use x)]))
    (matching-schema x ps/StructuredPart)
    (if-let [d (:best-end-date x)]
      d
      (best-end-date (get-in x [:structs (:struct-in-use x)])))
    :else
    (throw-invalid-end-date-record x)))

(s/defn remove-best-end-dates :- ps/Part
  "Returns a copy of `part` with all current `best-end-dates` values
  removed (or set to `nil`, as appropriate.)"
  [part :- ps/Part]
  (loop [loc (part-zipper part)]
    (if (pz/end? loc)
      (pz/root-part loc)
      (let [n (if (:best-end-date (pz/node-val loc))
                (remove-best-end-date loc)
                loc)]
        (recur (pz/next n))))))
