(ns route-ccrs.schema.ids.shop-order-id-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.ids :refer [ShopOrderId]]))

(def shop-order-no-re #"(?i)^(RMA|IFR|L)?\d+$")

(defn not-shop-order-no? [s]
  (nil? (re-find shop-order-no-re s)))

(def gen-shop-order-no
  (gen/fmap (partial apply str)
            (gen/tuple
              (gen/elements ["" "RMA" "IFR" "L"])
              (gen/such-that #(> % 0) gen/pos-int))))

(def gen-valid-release
  (gen/one-of
    [(gen/return "*")
     (gen/fmap str (gen/such-that #(> % 0) gen/pos-int))]))

(def gen-invalid-release
  (gen/such-that #(if (number? %) (< % 0) true) gen/simple-type))

(defn gen-shop-order-id
  ([] (gen-shop-order-id {}))
  ([o r s] (gen-shop-order-id {:order-no o :release r :sequence s}))
  ([{:keys [order-no release sequence]
     :or {order-no gen-shop-order-no
          release gen-valid-release
          sequence gen-valid-release}}]
   (gen/hash-map :order-no order-no :release release :sequence sequence)))

(def gen-invalid-shop-order-id
  (gen/one-of
    [; invalid order number
     (gen-shop-order-id
       {:order-no (gen/such-that not-shop-order-no? gen/string-alphanumeric)})
     ; invalid release
     (gen-shop-order-id {:release gen-invalid-release})
     ; invalid sequence
     (gen-shop-order-id {:sequence gen-invalid-release})
     ; every field invalid
     (gen-shop-order-id
       (gen/such-that not-shop-order-no? gen/string-alphanumeric)
       gen-invalid-release
       gen-invalid-release)
     ; not a map
     gen/simple-type]))

(defspec shop-order-id
  (prop/for-all [id (gen-shop-order-id)]
                (is-valid-to-schema ShopOrderId id)))

(defspec invalid-shop-order-id
  (prop/for-all [id gen-invalid-shop-order-id]
                (not-valid-to-schema ShopOrderId id)))
