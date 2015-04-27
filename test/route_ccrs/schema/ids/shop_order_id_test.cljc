(ns route-ccrs.schema.ids.shop-order-id-test
  (:require #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.ids :refer [ShopOrderId]]
            [route-ccrs.generators.shop-order
             :refer [gen-shop-order-id
                     gen-invalid-shop-order-id]]))

(defspec shop-order-id
  (prop/for-all [id (gen-shop-order-id)]
                (is-valid-to-schema ShopOrderId id)))

(defspec invalid-shop-order-id
  (prop/for-all [id gen-invalid-shop-order-id]
                (not-valid-to-schema ShopOrderId id)))
