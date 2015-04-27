(ns test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [route-ccrs.part-zipper-test]))

(set! *print-newline* false)
(set-print-fn! js/print)

(run-tests
  'route-ccrs.part-zipper-test)
