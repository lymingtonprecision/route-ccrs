(ns route-ccrs.schema.test-util
  (:require [clojure.test :refer :all]
            [schema.core :refer [check]]
            [clojure.test.check.generators :as gen]
            [clj-time.core :as t]))

(def ^:dynamic *such-that-retries* 100)

(defn is-valid-to-schema [s x]
  (is (nil? (check s x))))

(defn not-valid-to-schema [s x]
  (is (not (nil? (check s x)))))

(defn gen-with-extra-fields
  ([g] (gen-with-extra-fields g {}))
  ([g {:keys [max key-gen value-gen]
       :or {max nil
            key-gen gen/simple-type
            value-gen gen/simple-type}}]
   (let [exf (gen/not-empty (gen/map key-gen value-gen))]
     (if max
       (gen/resize max exf)
       exf))))

(def gen-date
  (gen/fmap #(t/plus (t/today) (t/days %)) gen/pos-int))

(defn gen-such-that
  ([pred gen] (gen-such-that pred gen *such-that-retries*))
  ([pred gen max-retries] (gen/such-that pred gen max-retries)))
