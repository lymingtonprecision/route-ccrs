(ns route-ccrs.schema.ids.purchase-order-id-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.ids :refer [PurchaseOrderId]]))

(def gen-invalid-line-no
  (gen-such-that #(if (number? %) (< % 0) true) gen/simple-type))

(defn gen-purchase-order-id
  ([] (gen-purchase-order-id {}))
  ([{:keys [order-no line release]
     :or {order-no (gen/not-empty (gen/resize 12 gen/string-alphanumeric))
          line (gen-such-that pos? gen/pos-int)
          release (gen-such-that pos? gen/pos-int)}}]
   (gen/hash-map :order-no order-no :line line :release release)))

(def gen-invalid-purchase-order-id
  (gen/one-of
    [; invalid order no
     (gen-purchase-order-id
       {:order-no (gen/one-of
                    [(gen/return "")
                     (gen-such-that #(> (count %) 12)
                                    (gen/resize 100 gen/string-alphanumeric))
                     (gen-such-that (complement string?) gen/simple-type)])})
     ; invalid line
     (gen-purchase-order-id
       {:line (gen/one-of [(gen/return 0) gen-invalid-line-no])})
     ; invalid release
     (gen-purchase-order-id
       {:release (gen/one-of [(gen/return 0) gen-invalid-line-no])})
     ; not a map
     (gen/return nil)
     gen/simple-type]))

(defspec purchase-order-id
  (prop/for-all [id (gen-purchase-order-id)]
                (is-valid-to-schema PurchaseOrderId id)))

(defspec invalid-purchase-order-id
  (prop/for-all [id gen-invalid-purchase-order-id]
                (not-valid-to-schema PurchaseOrderId id)))
