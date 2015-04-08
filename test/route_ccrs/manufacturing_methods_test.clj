(ns route-ccrs.manufacturing-methods-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.core :as s]
            [schema.test]
            [route-ccrs.manufacturing-methods :refer :all]))

(use-fixtures :once schema.test/validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; short-mm

(defspec short-mm-for-non-maps
  (prop/for-all [v (gen/such-that (complement map?) gen/simple-type)]
                (s/without-fn-validation
                  (is (nil? (short-mm v))))))

(defspec short-mm-for-invalid-maps 25
  (prop/for-all [v (gen/map gen/simple-type gen/simple-type)]
                (s/without-fn-validation
                  (is (= "" (short-mm v))))))

(deftest short-mm-for-mm-maps
  (is (= "m1*" (short-mm {:type :manufactured :revision 1 :alternative "*"})))
  (is (= "r23" (short-mm {:type :repair :revision 2 :alternative "3"})))
  (is (= "p10*" (short-mm {:type :purchased :revision 10 :alternative "*"}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mm-gt?

(deftest mm-gt-higher-revision-always-trumps
  (is (= true (mm-gt? {:type :purchased :revision 2 :alternative "7"}
                      {:type :purchased :revision 1 :alternative "*"}))))

(deftest mm-gt-default-alt-trumps-others
  (is (= false (mm-gt? {:type :purchased :revision 1 :alternative "7"}
                       {:type :purchased :revision 1 :alternative "*"}))))

(deftest mm-gt-lowest-alt-wins
  (is (= true (mm-gt? {:type :purchased :revision 1 :alternative "1"}
                      {:type :purchased :revision 1 :alternative "2"}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; preferred-mm

(deftest preferred-mm-with-no-mms
  (is (nil? (preferred-mm []))))

(deftest preferred-mm-with-single-mm
  (let [mm {:type :manufactured :revision 5 :alternative "2"}]
    (is (= mm (preferred-mm [mm])))))

(deftest preferred-mm-prefers-greatest-mm
  (let [ex {:type :manufactured :revision 6 :alternative "4"}
        mms [{:type :manufactured :revision 1 :alternative "*"}
             {:type :manufactured :revision 3 :alternative "*"}
             ex
             {:type :manufactured :revision 5 :alternative "2"}
             {:type :manufactured :revision 2 :alternative "*"}]]
    (is (= ex (preferred-mm mms)))))

(deftest preferred-mm-prefers-manufactured-over-purchased-over-repair
  (let [mms [{:type :manufactured :revision 2 :alternative "1"}
             {:type :purchased :revision 2 :alternative "*"}
             {:type :manufactured :revision 2 :alternative "2"}
             {:type :repair :revision 1 :alternative "*"}
             {:type :manufactured :revision 3 :alternative "*"}
             {:type :purchased :revision 2 :alternative "7"}]]
    (is (= :manufactured (:type (preferred-mm mms))))
    (is (= :purchased (:type (preferred-mm
                               (remove #(= :manufactured (:type %)) mms)))))
    (is (= :repair (:type (preferred-mm
                            (filter #(= :repair (:type %)) mms)))))))
