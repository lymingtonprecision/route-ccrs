(ns route-ccrs.generators.part-sourcing
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util :refer [gen-such-that]]
            [route-ccrs.generators.shop-order
             :refer [gen-shop-order-id gen-invalid-shop-order-id]]
            [route-ccrs.generators.purchase-order
             :refer [gen-purchase-order-id gen-invalid-purchase-order-id]]))

(def sources
  {:fixed-leadtime {:valid (gen-such-that (complement zero?) gen/pos-int)
                    :invalid (gen/one-of
                               [gen/neg-int
                                (gen-such-that (complement number?)
                                               gen/simple-type)])}
   :fictitious {:valid nil :invalid gen/simple-type}
   :stock {:valid nil :invalid gen/simple-type}
   :shop-order {:valid (gen-shop-order-id)
                :invalid gen-invalid-shop-order-id}
   :purchase-order {:valid (gen-purchase-order-id)
                    :invalid gen-invalid-purchase-order-id}})

(defn gen-source [t & generators]
  (gen/bind
    (gen/elements (keys sources))
    (fn [k]
      (let [vk (gen/return k)
            vv (-> sources (get k) (get t))
            v (if vv [vk vv] [vk])]
        (apply gen/tuple (concat v generators))))))
