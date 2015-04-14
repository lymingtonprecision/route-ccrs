(ns route-ccrs.best-end-dates
  "Provides the most basic level of interaction with best end dates:
  reading them from records.

  Exposes a single function `best-end-date` that, given a part,
  structure, or routing will return the current best end date of that
  record.

  Please see the sub-namespaces for functions interacting with the end
  dates within records in different ways."
  (:require [schema.core :as s]
            [route-ccrs.schema.parts :as ps]
            [route-ccrs.schema.routes :as rs]
            [route-ccrs.util :refer [defmethods]]
            [route-ccrs.util.schema-dispatch :refer [get-schema]]
            [clojure.zip :as zip]
            [route-ccrs.part-zipper :as pz :refer [part-zipper]]))

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
