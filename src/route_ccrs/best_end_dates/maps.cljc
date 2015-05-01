(ns route-ccrs.best-end-dates.maps
  "An abstraction for working with cached copies of calculated end dates.

  Provides two main functions:

  * `part->end-date-map` which extracts the best end dates from a part
    record into a `BestEndDateMap`.
  * `update-best-end-dates-from-map` which updates a part record with
    the end dates of matching entries in a `BestEndDateMap`.

  This makes it possible to extract structures piecemeal: one level at a
  time, rather than extracting them in full, and is here in support of
  batch processing of entire collections of parts (whereas all the other
  functions are mainly only concerned with a single part/structure.)

  The general process would be:

  * Get the lowest level parts.
  * Calculate their best end dates.
  * Extract the best end dates into a `BestEndDateMap`.
  * Get the next level of parts (this inherently includes the component
    parts but omits their details.)
  * Update the component part end dates from your `BestEndDateMap`.
  * Calculate the end dates of this level.
  * Extract the best end dates, merging with your `BestEndDateMap`.
  * Repeat for all higher levels."
  (:require [schema.core :as s]
            [route-ccrs.schema.best-end-dates :refer [BestEndDateMap]]
            [route-ccrs.schema.parts :as ps]

            [route-ccrs.part-zipper :as pz :refer [part-zipper]]
            [route-ccrs.best-end-dates :refer [remove-best-end-date]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; End date manipulation

(defn update-best-end-date-from-map
  "Given a part-zipper location and best end date map returns the same
  location modified to include the new best end date, if there's a
  matching entry in the provided best end dates.

  Will remove the best end date from the node if there is a `nil` entry
  in the best end date map."
  [n best-end-dates]
  (let [p (conj (pz/path-from-part-to-loc n) :best-end-date)
        d (get-in best-end-dates p ::no-date-given)]
    (if (= ::no-date-given d)
      n
      (if (nil? d)
        (remove-best-end-date n)
        (pz/edit-val n assoc :best-end-date d)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn part->end-date-map :- BestEndDateMap
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
    (if (pz/end? loc)
      bed
      (let [bed (if-let [ed (:best-end-date (pz/node-val loc))]
                  (assoc-in bed
                            (conj (pz/path-from-part-to-loc loc) :best-end-date)
                            ed)
                  bed)]
        (recur bed (pz/next loc))))))

(s/defn update-best-end-dates-from-map :- ps/Part
  "Returns a copy of `part` with the best end dates of appropriate child
  entries updated from the `BestEndDateMap` `best-end-dates`."
  [part :- ps/Part, best-end-dates :- BestEndDateMap]
  (loop [loc (part-zipper part)]
    (if (pz/end? loc)
      (pz/root-part loc)
      (recur (pz/next (update-best-end-date-from-map loc best-end-dates))))))
