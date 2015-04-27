(ns route-ccrs.schema.ids.purchase-order-id-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.ids :refer [PurchaseOrderId]]
            [route-ccrs.generators.purchase-order
             :refer [gen-purchase-order-id
                     gen-invalid-purchase-order-id]]))

(defspec purchase-order-id
  (prop/for-all [id (gen-purchase-order-id)]
                (is-valid-to-schema PurchaseOrderId id)))

(defspec invalid-purchase-order-id
  (prop/for-all [id gen-invalid-purchase-order-id]
                (not-valid-to-schema PurchaseOrderId id)))
