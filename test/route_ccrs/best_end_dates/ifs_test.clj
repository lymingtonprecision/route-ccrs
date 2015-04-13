(ns route-ccrs.best-end-dates.ifs-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.test]
            [route-ccrs.test-util :as tu]
            [yesql.core :refer [defquery]]
            [bugsbio.squirrel :as sq]

            [clj-time.core :as t]
            [clj-time.coerce :as tc]

            [route-ccrs.sql.serializers :refer :all]
            [route-ccrs.best-end-dates.ifs :refer :all]))

(def ^:dynamic *default-db-test-count* 10)

(defn init-test-system [test-fn]
  (tu/start-test-system)
  (test-fn))

(use-fixtures :once init-test-system schema.test/validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; shop-order-end-date

(deftest shop-order-end-dates-for-invalid-orders
  (is (nil? (-ifs-shop-order-end-date (:db user/system)
                                      {:order-no "L9999"
                                       :release "999"
                                       :sequence "999"}))))

(defquery -shop-orders "queries/shop_orders_with_due_dates.sql")
(defquery -closed-shop-orders "queries/shop_orders_with_close_dates.sql")

(defspec shop-order-end-dates-match-due-date *default-db-test-count*
  (prop/for-all [so (gen/elements
                      (-shop-orders
                        {}
                        {:connection (:db @tu/test-system)
                         :row-fn #(sq/to-clj % {:due-date date-serializer})}))]
                (is (= (:due-date so)
                       (-ifs-shop-order-end-date (:db @tu/test-system)
                                                 (dissoc so :due-date))))))

(defspec closed-shop-order-end-dates-match-close-date *default-db-test-count*
  (prop/for-all [so (gen/elements
                      (-closed-shop-orders
                        {}
                        {:connection (:db @tu/test-system)
                         :row-fn #(sq/to-clj % {:close-date date-serializer})}))]
                (is (= (:close-date so)
                       (-ifs-shop-order-end-date (:db @tu/test-system)
                                                 (dissoc so :close-date))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; purchase-order-end-date

(deftest purchase-order-end-dates-for-invalid-orders
  (is (nil? (-ifs-purchase-order-end-date (:db user/system)
                                          {:order-no "13045781"
                                           :line 999
                                           :release 999}))))

(defquery -purchase-lines "queries/purchase_order_lines_with_due_dates.sql")
(defquery -cancelled-purchase-lines "queries/purchase_order_lines_cancelled.sql")

(defspec purchase-order-end-dates-match-planned-receipt-date
  (prop/for-all [po (gen/elements
                      (-purchase-lines
                        {}
                        {:connection (:db @tu/test-system)
                         :row-fn #(sq/to-clj % {:line int-serializer
                                                :release int-serializer
                                                :due-date date-serializer})}))]
                (is (= (:due-date po)
                       (-ifs-purchase-order-end-date (:db @tu/test-system)
                                                     (dissoc po :due-date))))))

(defspec cancelled-purchase-order-end-dates
  (prop/for-all [po (gen/elements
                      (-cancelled-purchase-lines
                        {}
                        {:connection (:db @tu/test-system)
                         :row-fn #(sq/to-clj % {:line int-serializer
                                                :release int-serializer})}))]
                (is (nil? (-ifs-purchase-order-end-date
                            (:db @tu/test-system) po)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; interval-end-date

(deftest interval-zero-is-today
  ;; Yes, this was an actual failing test case:
  ;; the dates being returned from the DB were -01:00 from what they
  ;; should have been: 2015-04-08T23:00:00 instead of 2015-04-09T00:00
  ;;
  ;; This should never fail on a working day, and you're only working
  ;; on it on a working day right?
  (is (= (tc/to-local-date (t/today))
         (-ifs-interval-end-date (:db @tu/test-system) 0))))
