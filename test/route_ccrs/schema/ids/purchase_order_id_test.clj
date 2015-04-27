(ns route-ccrs.schema.ids.purchase-order-id-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.generators.purchase-order :refer :all]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.ids :refer [PurchaseOrderId]]))

(defspec purchase-order-id
  (prop/for-all [id (gen-purchase-order-id)]
                (is-valid-to-schema PurchaseOrderId id)))

(defspec invalid-purchase-order-id
  (prop/for-all [id gen-invalid-purchase-order-id]
                (not-valid-to-schema PurchaseOrderId id)))
