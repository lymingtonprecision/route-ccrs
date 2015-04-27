(ns route-ccrs.schema.test-util
  (:require [clojure.test :refer :all]
            [schema.core :refer [check]]))

(defn is-valid-to-schema [s x]
  (is (nil? (check s x))))

(defn not-valid-to-schema [s x]
  (is (not (nil? (check s x)))))
