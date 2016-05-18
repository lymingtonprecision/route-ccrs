(ns route-ccrs.best-end-dates.sourcing-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [schema.test]
            [clj-time.core :as t]
            [route-ccrs.best-end-dates.dummy-resolver :refer [dummy-resolver]]
            [route-ccrs.best-end-dates.protocols :refer :all]
            [route-ccrs.best-end-dates.sourcing :refer :all]))

(use-fixtures :once schema.test/validate-schemas)

(defn rand-date [] (t/plus (t/today) (t/days (rand-int 999))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; end-date-from-source

(deftest non-schema-validated-non-source-record-returns-nil
  (s/without-fn-validation
   (is (nil? (end-date-from-source {:has-no :source} dummy-resolver)))))

(deftest non-schema-validated-invalid-source-throws-arg-exception
  (s/without-fn-validation
   (is (thrown? IllegalArgumentException
                (end-date-from-source {:source 'invalid} dummy-resolver)))))

(deftest fictitious-sources-end-on-today
  (is (= (t/today) (end-date-from-source {:source [:fictitious]} dummy-resolver))))

(deftest fictitious-sources-end-on-start-date-when-provided
  (let [d (rand-date)]
    (is (= d (end-date-from-source {:source [:fictitious]} dummy-resolver d)))))

(deftest stock-sources-end-on-today
  (is (= (t/today) (end-date-from-source {:source [:stock]} dummy-resolver))))

(deftest stock-sources-end-on-start-date-when-provided
  (let [d (rand-date)]
    (is (= d (end-date-from-source {:source [:stock]} dummy-resolver d)))))

(deftest leadtime-sources-are-interval-calculated
  (is (= (interval-end-date dummy-resolver 10)
         (end-date-from-source {:source [:fixed-leadtime 10]} dummy-resolver)))
  (let [dr (assoc dummy-resolver :interval-factor (rand-int 99))]
    (is (= (interval-end-date dr 10)
           (end-date-from-source {:source [:fixed-leadtime 10]} dr)))))

(deftest leadtime-sources-are-calculated-from-start-date
  (let [d (rand-date)
        i (rand-int 999)]
    (is (= (interval-end-date dummy-resolver i d)
           (end-date-from-source
            {:source [:fixed-leadtime i]} dummy-resolver d)))))

(deftest shop-order-sources-are-resolved
  (let [id {:order-no "1234" :release "*" :sequence "*"}
        d (rand-date)
        dr (assoc dummy-resolver :end-dates {id d})]
    (is (= d (end-date-from-source {:source [:shop-order id]} dr)))
    (is (nil? (end-date-from-source
               {:source [:shop-order
                         {:order-no "L1234" :release "1" :sequence "1"}]}
               dr)))))

(deftest purchase-order-sources-are-resolved
  (let [id {:order-no "1234" :line 1 :release 1}
        d (rand-date)
        dr (assoc dummy-resolver :end-dates {id d})]
    (is (= d (end-date-from-source {:source [:purchase-order id]} dr)))
    (is (nil? (end-date-from-source
               {:source [:purchase-order
                         {:order-no "12301481" :line 12 :release 4}]}
               dr)))))
