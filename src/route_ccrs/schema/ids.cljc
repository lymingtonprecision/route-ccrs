(ns route-ccrs.schema.ids
  (:require [clojure.string :as str]
            [schema.core :as s]
            [route-ccrs.schema.generic :as g]))

(def PartNo
  "A part number is a string matching the format `1001_____R__*`, where
  `_____` is a five digit string of numbers and `__*` is a two digit or
  longer string of numbers."
  (s/both
   s/Str
   (s/pred
    #(re-find #"^1001\d{5}R\d{2,}$" (str/upper-case %))
    'valid-part-no)))

(def ManufacturingMethod
  "A manufacturing method has *only* the following fields:

  `:type` either `:manufactured`, `:purchased`, or `:repair`
  `:revision` a greater than zero integer
  `:alternative` a string of either: `*` or a positive, non-zero, integer"
  {:type (s/enum :manufactured :purchased :repair)
   :revision g/int-gt-zero
   :alternative g/asterisk-or-number})

(def ManufacturedMethodId
  "A manufactured method ID is a manufacturing method that is only valid
  for the `:manufactured` and `:repair` types."
  (assoc ManufacturingMethod :type (s/enum :manufactured :repair)))

(def PurchasedMethodId
  "A purchased method ID is a manufacturing method that is only valid
  for the `:purchased`."
  (assoc ManufacturingMethod :type (s/eq :purchased)))

(def ShopOrderNo
  "A shop order number is a string of numbers optionally prefixed by
  'RMA', 'IFR', or 'L'."
  (s/both s/Str (s/pred #(re-find #"(?i)^(RMA|IFR|L)?\d+$" %) 'shop-order-no)))

(def ShopOrderId
  "A shop order ID consists of *only* the fields:

  `:order-no` a shop order number (see `ShopOrderNo`)
  `:release` a string of either: `*` or a positive, non-zero, integer
  `:sequence` as per `:release`"
  {:order-no ShopOrderNo
   :release g/asterisk-or-number
   :sequence g/asterisk-or-number})

(def PurchaseOrderId
  "A purchase order ID consists of *only* the fields:

  `:order-no` a non-empty string or up to 12 characters
  `:line` an integer greater than zero
  `:release` an integer greater than zero"
  {:order-no (s/both s/Str
                     (s/pred not-empty 'not-empty)
                     (s/pred #(<= (count %) 12) 'lt-12-characters))
   :line g/int-gt-zero
   :release g/int-gt-zero})
