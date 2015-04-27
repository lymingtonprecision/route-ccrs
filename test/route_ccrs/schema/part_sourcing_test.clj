(ns route-ccrs.schema.part-sourcing-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.core :refer [check]]
            [route-ccrs.generators.part-sourcing :refer :all]
            [route-ccrs.generators.shop-order :as so]
            [route-ccrs.generators.purchase-order :as po]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.parts :refer [Source Sourced]]))

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
