(ns route-ccrs.manufacturing-methods
  (:require [schema.core :as s]
            [route-ccrs.schema.ids :as ids]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(defn str->int [s]
  #?(:clj  (java.lang.Integer/parseInt s)
     :cljs (js/parseInt s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn short-mm :- (s/maybe s/Str)
  "Returns a short string representation of a manufacturing method ID,
  or `nil` if the manufacturing method is `nil`.

  ```clojure
  (short-mm {:type :manufactured :revision 1 :alternative \"*\"})
  ;=> \"m1*\"
  ```"
  [mm :- (s/maybe ids/ManufacturingMethod)]
  (if (map? mm)
    (str (second (str (:type mm)))
       (:revision mm)
       (:alternative mm))))

(s/defn mm-gt? :- s/Bool
  "Returns true if the manufacturing method `x` is logically greater
  than the manufacturing method `y`.

  A manufacturing method is greater than another if:

  * Its revision is greater.
  * It's the default alternative (of the same revision.)
  * It's a _lower_ alternative (of the same revision.)"
  [x :- ids/ManufacturingMethod, y :- ids/ManufacturingMethod]
  (let [rev-compare (compare (:revision x) (:revision y))
        x-default? (= "*" (:alternative x))
        y-default? (= "*" (:alternative y))
        alt-compare (if (and (not x-default?) (not y-default?))
                      (compare (str->int (:alternative x))
                               (str->int (:alternative y))))]
    (cond
      (> rev-compare 0) true
      (< rev-compare 0) false
      x-default? true
      y-default? false
      (< alt-compare 0) true
      (> alt-compare 0) false
      :else false)))

(s/defn preferred-mm :- (s/maybe ids/ManufacturingMethod)
  "Returns the manufacturing method from the collection `mm` that
  should be preferred when producing the part. This will normally
  be the default alternative of the latest revision.

  `nil` is returned if `mm` is empty.

  The different types of method are preferred in this order:
  `manufacturing > purchased > repair`"
  [mm :- [ids/ManufacturingMethod]]
  (let [types (partition-by :type mm)
        type-defaults (reduce
                        (fn [r t] (assoc r (:type t) t))
                        {}
                        (map
                          #(reduce
                             (fn [r mm]
                               (cond
                                 (nil? r) mm
                                 (mm-gt? mm r) mm
                                 :else r))
                             nil
                             %)
                          types))]
    (or (:manufactured type-defaults)
        (:purchased type-defaults)
        (:repair type-defaults))))
