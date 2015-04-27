(ns route-ccrs.schema.ids.shop-order-id-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.generators.shop-order :refer :all]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.ids :refer [ShopOrderId]]))

(defspec shop-order-id
  (prop/for-all [id (gen-shop-order-id)]
                (is-valid-to-schema ShopOrderId id)))

(defspec invalid-shop-order-id
  (prop/for-all [id gen-invalid-shop-order-id]
                (not-valid-to-schema ShopOrderId id)))
