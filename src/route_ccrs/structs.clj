(ns route-ccrs.structs
  (:require [yesql.core :refer [defquery]]
            [route-ccrs.processing :refer :all]))

(defquery components-for-part "route_ccrs/sql/buildable_structures_for_part.sql")

(def structure-keys
  [:contract
   :part_no
   :eng_chg_level
   :bom_type
   :alternative_no])

(defn structure-id [r]
  (select-keys r structure-keys))

(defn transduce-structures
  "Transduces a list of structure components into tuples of
  `[structure-id components]` where `structure-id` is a map of the fields
  uniquely identifying the structure and `components` is a collection of
  the components that comprise the structure.

  Requires the component records to have a `:component_count` value
  signifying the number of components in the structure to which they
  belong. Otherwise no structures will be returned until the transducer
  is asked to complete and flush its state."
  [step]
  (transduce-by-shared-id structure-id #(vec []) :component_count step))

(defn deferred-structs [db part]
  (promise-transduced-query
    db
    (partial components-for-part part)
    transduce-structures
    {}))
