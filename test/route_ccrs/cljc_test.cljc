(ns route-ccrs.cljc-test
  "A stub namespace to run tests in `.cljc` files.

  Needed to run tests in ClojureScript *and* Clojure (until `lein test`
  natively supports `.cljc` files.)"
  (:require #?(:clj  [clojure.test :as test]
               :cljs [cljs.test :as test :include-macros true])
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
            [route-ccrs.schema.active-part-test]
            [route-ccrs.schema.part-sourcing-test]
            [route-ccrs.schema.purchased-raw-part-test]
            [route-ccrs.schema.structures.purchased-test]
            [route-ccrs.schema.structures.manufacturing-test]
            [route-ccrs.schema.structured-part-test]
            [route-ccrs.schema.structure-test]
            [route-ccrs.schema.part-test]
            [route-ccrs.best-end-dates-test]
            [route-ccrs.best-end-dates.maps-test]
            [route-ccrs.start-dates-test]
            [route-ccrs.manufacturing-methods-test]
            [route-ccrs.part-zipper-test]))

#?(:cljs (enable-console-print!))

#?(:cljs
    (defmethod test/report [::test/default :summary] [m]
      (println "\nRan" (:test m) "tests containing"
               (+ (:pass m) (:fail m) (:error m)) "assertions.")
      (println (:fail m) "failures," (:error m) "errors.")
      (aset js/window "test-failures" (+ (:fail m) (:error m)))))

(defn test-runner []
  (test/run-tests
    ; id schema
    'route-ccrs.schema.date-test
    'route-ccrs.schema.ids.part-no-test
    'route-ccrs.schema.ids.manufacturing-method-test
    'route-ccrs.schema.ids.shop-order-id-test
    'route-ccrs.schema.ids.purchase-order-id-test
    ; route schema
    'route-ccrs.schema.routes.work-center-test
    'route-ccrs.schema.routes.operation-test
    'route-ccrs.schema.routes.ccr-test
    'route-ccrs.schema.routes.calculation-results-test
    'route-ccrs.schema.routes-test
    ; part schema
    'route-ccrs.schema.active-part-test
    'route-ccrs.schema.part-sourcing-test
    'route-ccrs.schema.purchased-raw-part-test
    ; struct schema
    'route-ccrs.schema.structures.purchased-test
    'route-ccrs.schema.structures.manufacturing-test
    'route-ccrs.schema.structured-part-test
    'route-ccrs.schema.structure-test
    'route-ccrs.schema.part-test
    ; fn tests
    'route-ccrs.best-end-dates-test
    'route-ccrs.best-end-dates.maps-test
    'route-ccrs.start-dates-test
    'route-ccrs.manufacturing-methods-test
    'route-ccrs.part-zipper-test))

#?(:clj (defn test-ns-hook [] (test-runner)))
