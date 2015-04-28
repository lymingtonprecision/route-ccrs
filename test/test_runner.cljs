(ns test-runner
  (:require [cljs.test :as test :refer-macros [run-tests] :refer [report]]
            [route-ccrs.schema.date-test]
            [route-ccrs.schema.ids.part-no-test]
            [route-ccrs.schema.ids.manufacturing-method-test]
            [route-ccrs.schema.ids.shop-order-id-test]
            [route-ccrs.schema.ids.purchase-order-id-test]
            [route-ccrs.schema.routes.work-center-test]
            [route-ccrs.schema.routes.operation-test]
            [route-ccrs.schema.routes.ccr-test]
            [route-ccrs.schema.routes.calculation-results-test]
            [route-ccrs.schema.routes-test]
            [route-ccrs.part-zipper-test]))

(enable-console-print!)

(defmethod report [::test/default :summary] [m]
  (println "\nRan" (:test m) "tests containing"
           (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors.")
  (aset js/window "test-failures" (+ (:fail m) (:error m))))

(defn runner []
  (test/run-tests
    (test/empty-env ::test/default)
    'route-ccrs.schema.date-test
    'route-ccrs.schema.ids.part-no-test
    'route-ccrs.schema.ids.manufacturing-method-test
    'route-ccrs.schema.ids.shop-order-id-test
    'route-ccrs.schema.ids.purchase-order-id-test
    'route-ccrs.schema.routes.work-center-test
    'route-ccrs.schema.routes.operation-test
    'route-ccrs.schema.routes.ccr-test
    'route-ccrs.schema.routes.calculation-results-test
    'route-ccrs.schema.routes-test
    'route-ccrs.part-zipper-test))
