(ns route-ccrs.util-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.core :as s]
            [route-ccrs.util :refer :all]
            [route-ccrs.util.schema-dispatch :as sd]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; schema-dispatch/get-schema

(declare mm)

(deftest get-schema-returns-nil-if-no-schema-matches
  (with-redefs [mm nil]
    (defmulti mm (fn [x] (sd/get-schema mm x)))
    (is (nil? (sd/get-schema mm {})))))

(deftest get-schema-returns-matching-schema
  (let [FooInt {:foo s/Int}
        FooStr {:foo s/Str}]
    (with-redefs [mm nil]
      (defmulti mm (fn [x] (sd/get-schema mm x)))
      (defmethod mm FooInt [x] :int)
      (defmethod mm FooStr [x] :str)
      (is (= FooInt (sd/get-schema mm {:foo 1})))
      (is (= FooStr (sd/get-schema mm {:foo "a"}))))))

(deftest get-schema-causes-dispatch-on-schema
  (let [FooInt {:foo s/Int}
        FooStr {:foo s/Str}]
    (with-redefs [mm nil]
      (defmulti mm (fn [x] (sd/get-schema mm x)))
      (defmethod mm FooInt [x] :int)
      (defmethod mm FooStr [x] :str)
      (is (= :int (mm {:foo 1})))
      (is (= :str (mm {:foo "a"}))))))

(deftest get-schema-dispatches-to-default-on-no-match
  (with-redefs [mm nil]
    (defmulti mm (fn [x] (sd/get-schema mm x)))
    (defmethod mm :default [_] :no-match)
    (is (= :no-match (mm {:foo 1})))))

(deftest get-schema-uses-schema-metadata
  (let [FooInt {:foo s/Int}
        FooStr {:foo s/Str}]
    (with-redefs [mm nil]
      (defmulti mm (fn [x] (sd/get-schema mm x)))
      (defmethod mm FooInt [x] :int)
      (defmethod mm FooStr [x] :str)
      (defmethod mm :default [_] :no-match)
      (is (= :str (mm (with-meta {:foo 1} {:schema FooStr}))))
      (is (= :no-match (mm (with-meta {:foo 1} {:schema :invalid})))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; schema-dispatch/get-schema-method

(deftest get-schema-method
  (let [FooInt {:foo s/Int}
        foo {:foo 1}
        bar {:bar foo}]
    (with-redefs [mm nil]
      (defmulti mm (fn [x] (sd/get-schema mm x)))
      (defmethod mm FooInt [x] :int)
      (is (= :int ((sd/get-schema-method mm foo) foo)))
      (is (nil? (sd/get-schema-method mm bar)))
      (do
        (defmethod mm :default [_] :no-match)
        (is (= :no-match ((sd/get-schema-method mm bar) bar)))))))
