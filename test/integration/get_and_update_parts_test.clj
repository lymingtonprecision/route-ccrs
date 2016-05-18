(ns integration.get-and-update-parts-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [schema.test]
            [route-ccrs.test-util :as tu]
            [yesql.core :refer [defquery]]

            [route-ccrs.best-end-dates.update :refer :all]
            [route-ccrs.part-store :as ps]))

(defn wrap-with-test-system [test-fn]
  (tu/start-test-system)
  (let [r (test-fn)]
    (tu/stop-test-system)
    r))

(use-fixtures :once wrap-with-test-system schema.test/validate-schemas)

(defquery -raw-parts "queries/raw_parts.sql")
(defquery -full-parts "queries/active_and_valid_full_bom_parts.sql")

(defspec ^:integration updating-raw-part-end-dates
  (prop/for-all
   [pno (gen/elements (-raw-parts {} {:connection (:db @tu/test-system)}))]
   (let [dc (:date-calculator @tu/test-system)
         p (->> pno
                (ps/get-part (:part-store @tu/test-system))
                remove-best-end-dates)
         up (update-all-best-end-dates-under-part p dc)]
     (is (not= p up))
     (is (= p (remove-best-end-dates up))))))

(defspec ^:integration updating-structured-part-end-dates
  (prop/for-all
   [pno (gen/elements (-full-parts {:min_depth 0}
                                   {:connection (:db @tu/test-system)}))]
   (let [dc (:date-calculator @tu/test-system)
         p (->> pno
                (ps/get-part (:part-store @tu/test-system))
                remove-best-end-dates)
         up (update-all-best-end-dates-under-part p dc)]
     (is (not= p up))
     (is (= p (remove-best-end-dates up))))))
