(ns route-ccrs.schema.ids.part-no-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [clojure.test.check.properties :as prop]
            [schema.core :as schema :refer [check]]
            [route-ccrs.schema.ids :refer [PartNo]]))

(def gen-part-no (gen'/string-from-regex #"1001\d{5}R\d{2,}"))

(def gen-invalid-part-no
  (gen/one-of
    [(gen'/string-from-regex #"100([02-9]\d{5}|\d{6,})R(|.+)")
     gen/string
     gen/simple-type]))

(defspec valid-part-numbers
  (prop/for-all [pn gen-part-no] (is (nil? (check PartNo pn)))))

(defspec invalid-part-numbers
  (prop/for-all [pn gen-invalid-part-no] (is (not (nil? (check PartNo pn))))))
