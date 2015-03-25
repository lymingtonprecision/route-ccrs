(ns route-ccrs.util
  "General collection of utility functions/macros that don't have a
  sensible home elsewhere."
  (:require [schema.core :as s]
            [route-ccrs.schema.parts :as ps]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public
(defmacro defmethods
  "A convenience over writing lots of short `defmethod` statements that
  use the same arguments:

       (defmulti mm (fn [o x y] o))
       (defmethods mm [_ x y]
         '+ (+ x y)
         '- (- x y)
         '* (* x y)
         '/ (/ x y))

       (mm '+ 1 2) ; => 3

  Shorter and more readable than the alternative."
  [mm fn-args & dispatch-map]
  (assert (even? (count dispatch-map)))
  (let [dm (partition 2 dispatch-map)]
    `(do ~@(map (fn [[dv f]] `(defmethod ~mm ~dv ~fn-args ~f)) dm))))

(defn sourced?
  "Returns truthy if `x` matches the `Sourced` schema."
  [x]
  (nil? (s/check ps/Sourced x)))
