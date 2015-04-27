(ns route-ccrs.schema.parts
  #?(:cljs (:require-macros [route-ccrs.schema.variant :refer [variant-schema]]))
  (:require [schema.core :as s]
            [route-ccrs.schema.ids :as id]
            [route-ccrs.schema.dates :as d]
            [route-ccrs.schema.generic :as g]
            #?(:clj [route-ccrs.schema.variant :refer [variant-schema]])
            [route-ccrs.schema.routes :refer [map->Routed]]))

(declare Part)

(def ActivePart
  "An active part has *only* the fields:

  `:id` a valid part number (see `PartNo`)
  `:low-level-code` either zero or a positive integer"
  {:id id/PartNo
   :low-level-code s/Int})

(def Source
  "A source is a variant of one of the following types:

  * `[:fixed-leadtime lt]` where `lt` is a non-zero positive integer
  * `[:fictitious]`
  * `[:stock]`
  * `[:shop-order shop-order-id]` see `ShopOrderId`
  * `[:purchase-order purchase-order-id]` see `PurchaseOrderId`"
  (variant-schema
    :fixed-leadtime (s/one g/int-gt-zero 'leadtime)
    :fictitious nil
    :stock nil
    :shop-order (s/one id/ShopOrderId 'shop-order-id)
    :purchase-order (s/one id/PurchaseOrderId 'purchase-order-id)))

(def Sourced
  "A record is considered sourced if it has a `:source` field set to a
  valid `Source`."
  {:source Source
   s/Any s/Any})

(defn sourced?
  "Returns truthy if `x` matches the `Sourced` schema."
  [x]
  (nil? (s/check Sourced x)))

(def ComponentList
  "A component list is a map where the keys are any non-nil value and
  the values are valid `Part` records.

  (Note: component lists are _maps_ rather than _sequences_ so that
  `diff`ing them is more deterministic than relying on sort order.)"
  {g/any-non-nil (s/recursive #'Part)})

(def PurchasedStructure
  "A purchasing structure consists of *only* the following fields:

  `:id` a valid `PurchasedMethodId`
  `:components` a valid `ComponentList` (cannot be `nil`)
  `:lead-time` zero, or a positive integer
  `:best-end-date` nil, or a valid `DateInst`
  `:description` an optional string describing the structure"
  {:id id/PurchasedMethodId
   (s/optional-key :description) (s/maybe s/Str)
   :components ComponentList
   :lead-time g/int-gte-zero
   :best-end-date (s/maybe d/DateInst)})

(def route-types-match-structure?
  "A predicate to ensure that any `:routes` associated with a structure
  match the manufacturing method of the structure (i.e. that all routes
  for a `:manufactured` structure are also `:manufactured`, or a
  `:repair` structure has only `:repair` routes, etc.)"
  (s/pred
    (fn [s]
      (let [t (->> s :id :type)]
        (every? (fn [[_ r]] (= (->> r :id :type) t)) (:routes s))))
    'route-types-match-structure))

(def ManufacturedStructure
  "A manufacturing structure consists of the following fields:

  `:id` a valid `ManufacturedMethodId`
  `:components` a valid `ComponentList` (cannot be `nil`)

  It may optionally have a `:description`.

  Additionally a manufacturing structure is a `RoutedItem` and so must
  also conform to that schema. Note that the `:routes` assigned to the
  structure *must* match its manufacturing method `:type` and cannot be
  empty.

  No fields beyond those listed above or present in the `RoutedItem`
  schema are permitted."
  (map->Routed
    {:id id/ManufacturedMethodId
     (s/optional-key :description) (s/maybe s/Str)
     :components ComponentList}
    route-types-match-structure?))

(def Structure
  "A structure has the following fields:

  `:id` a valid `ManufacturingMethod`
  `:components` a `ComponentList`

  It may optionally have a `:description`.

  ... and any additional fields as dictated by the `:type` of its
  manufacturing method."
  (s/both
    {:id id/ManufacturingMethod
     :components ComponentList
     s/Any s/Any}
    (s/conditional
      #(nil? (s/check id/PurchasedMethodId (:id %))) PurchasedStructure
      #(nil? (s/check id/ManufacturedMethodId (:id %))) ManufacturedStructure)))

(def StructureList
  "A structure list is a map where the keys are any non-nil value and
  the values are valid `Structure` records."
  {g/any-non-nil Structure})

(def valid-structure-in-use?
  "Predicate to verify that a `StructuredItem`s `:struct-in-use`
  corresponds to an entry in its `:structs` list."
  (s/pred #(contains? (:structs %) (:struct-in-use %)) 'valid-struct-in-use))

(defn map->Structured
  "Takes a schema _map_, `sm`, and returns a new schema that is a copy
  of it with the `StructuredItem` fields and `valid-structure-in-use?`
  predicate added.

  Any additional arguments are assumed to be further schemas which
  the resulting schema should subsume."
  [sm & s]
  (apply
    s/both
    (merge sm {:structs StructureList :struct-in-use g/any-non-nil})
    valid-structure-in-use?
    s))

(def StructuredItem
  "A structured item is a record that contains the fields:

  `:structs` a valid `StructureList`
  `:struct-in-use` a value identifying an entry in `:structs`

  The value of `:struct-in-use` *cannot* be `nil` and *must* be a valid
  key within the `:structs` collection."
  (map->Structured {s/Any s/Any}))

(def common-part-fields
  {:id id/PartNo
   (s/optional-key :customer-part) (s/maybe s/Str)
   (s/optional-key :issue) (s/maybe s/Str)
   (s/optional-key :description) (s/maybe s/Str)
   :best-end-date (s/maybe d/DateInst)
   (s/optional-key :source) Source})

(def StructuredPart
  "A structured part *must* have the fields:

  `:id` a valid `PartNo`
  `:type` set to `:structured`
  `:best-end-date` `nil`, or a valid `DateInst`

  It may optionally have the fields:

  `:customer-part` the customer part number
  `:issue` the issue of the customer part this part represents
  `:description` a description of the part

  It *must* also conform to the `StructuredItem` schema and may
  _optionally_ be a `Sourced` record, containing a non-nil `:source`
  entry.

  No other fields are permitted."
  (map->Structured
    (assoc common-part-fields :type (s/eq :structured))))

(def PurchasedRawPart
  "A purchased raw part *must* have the fields:

  `:id` a valid `PartNo`
  `:type` set to `:raw`
  `:lead-time` zero, or a positive integer
  `:best-end-date` `nil`, or a valid `DateInst`

  It may optionally have the fields:

  `:customer-part` the customer part number
  `:issue` the issue of the customer part this part represents
  `:description` a description of the part

  It may _optionally_ be a `Sourced` record, containing a non-nil
  `:source` entry.

  No other fields are permitted."
  (merge
    common-part-fields
    {:type (s/eq :raw)
     :lead-time g/int-gte-zero}))

(def Part
  "A part is a record that has the fields:

  `:id` a valid `PartNo`
  `:type` either `:raw` or `:structured`

  ... and conforms to the schema corresponding to its type
  (`PurchasedRawPart` or `StructuredPart`, respectively.)"
  (s/both
    {:id id/PartNo
     :type (s/enum :raw :structured)
     s/Any s/Any}
    (s/conditional
      #(= (:type %) :raw) PurchasedRawPart
      #(= (:type %) :structured) StructuredPart)))
