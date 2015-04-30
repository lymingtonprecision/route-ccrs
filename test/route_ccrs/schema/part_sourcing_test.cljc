(ns route-ccrs.schema.part-sourcing-test
  #?(:cljs (:require-macros [cljs.test :refer [deftest is]]))
  (:require #?(:clj  [clojure.test :refer [deftest is]]
               :cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.generators.part-sourcing :refer [gen-source]]
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.parts :refer [Source Sourced sourced?]]))

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
  10
  (prop/for-all [s (gen-source :valid)
                 m (gen/map gen/simple-type gen/simple-type)]
                (is-valid-to-schema Sourced (merge m {:source s}))
                (not-valid-to-schema Sourced m)
                (not-valid-to-schema Sourced (merge m {:source nil}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; sourced?

(deftest records-matching-sourced-schema-are-sourced
  (is (sourced? {:source [:fictitious]})))

(deftest empty-maps-arent-sourced
  (is (not (sourced? {}))))

(defspec non-map-types-arent-sourced
  (prop/for-all [x gen/simple-type] (is (not (sourced? x)))))
