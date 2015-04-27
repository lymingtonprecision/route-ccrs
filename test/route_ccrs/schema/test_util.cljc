(ns route-ccrs.schema.test-util
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [is]])
            [schema.core :refer [check]]))

(defn is-valid-to-schema [s x]
  (is (nil? (check s x))))

(defn not-valid-to-schema [s x]
  (is (not (nil? (check s x)))))
