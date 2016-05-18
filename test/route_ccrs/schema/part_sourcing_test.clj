(ns route-ccrs.schema.part-sourcing-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.core :refer [check]]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.ids.shop-order-id-test :as so]
            [route-ccrs.schema.ids.purchase-order-id-test :as po]
            [route-ccrs.schema.parts :refer [Source Sourced]]))

(def sources
  {:fixed-leadtime {:valid (gen-such-that (complement zero?) gen/pos-int)
                    :invalid (gen/one-of
                               [gen/neg-int
                                (gen-such-that (complement number?)
                                               gen/simple-type)])}
   :fictitious {:valid nil :invalid gen/simple-type}
   :stock {:valid nil :invalid gen/simple-type}
   :shop-order {:valid (so/gen-shop-order-id)
                :invalid so/gen-invalid-shop-order-id}
   :purchase-order {:valid (po/gen-purchase-order-id)
                    :invalid po/gen-invalid-purchase-order-id}})

(defn gen-source [t & generators]
  (gen/bind
    (gen/elements (keys sources))
    (fn [k]
      (let [vk (gen/return k)
            vv (-> sources (get k) (get t))
            v (if vv [vk vv] [vk])]
        (apply gen/tuple (concat v generators))))))

(defspec valid-sources
  (prop/for-all [v (gen-source :valid)]
                (is-valid-to-schema Source v)))

(defspec invalid-sources
  (prop/for-all [v (gen-source :invalid)]
                (not-valid-to-schema Source v)))

(defspec extra-elements
  (prop/for-all [v (gen-source :valid gen/simple-type)]
                (not-valid-to-schema Source v)))

(defspec sourced-record
  (prop/for-all [s (gen-source :valid)]
                (is-valid-to-schema Sourced {:source s})))

(defspec sourced-record-extra-fields
  (prop/for-all [s (gen-source :valid)
                 m (gen/map gen/simple-type gen/simple-type)]
                (is-valid-to-schema Sourced (merge m {:source s}))
                (not-valid-to-schema Sourced m)
                (not-valid-to-schema Sourced (merge m {:source nil}))))
