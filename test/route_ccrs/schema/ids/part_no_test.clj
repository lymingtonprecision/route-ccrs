(ns route-ccrs.schema.ids.part-no-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [clojure.test.check.properties :as prop]
            [schema.core :as schema :refer [check]]
            [route-ccrs.schema.ids :refer [PartNo]]
            [route-ccrs.generators.part-no :refer :all]))

(defspec valid-part-numbers
  (prop/for-all [pn gen-part-no] (is (nil? (check PartNo pn)))))

(defspec invalid-part-numbers
  (prop/for-all [pn gen-invalid-part-no] (is (not (nil? (check PartNo pn))))))
