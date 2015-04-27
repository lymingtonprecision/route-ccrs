(ns route-ccrs.schema.ids.part-no-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [is]])
            #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [schema.core :as schema :refer [check]]
            [route-ccrs.schema.ids :refer [PartNo]]
            [route-ccrs.generators.part-no
             :refer [gen-part-no gen-invalid-part-no]]))

(defspec valid-part-numbers
  (prop/for-all [pn gen-part-no] (is (nil? (check PartNo pn)))))

(defspec invalid-part-numbers
  (prop/for-all [pn gen-invalid-part-no] (is (not (nil? (check PartNo pn))))))
