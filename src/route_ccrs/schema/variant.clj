(ns route-ccrs.schema.variant
  (:require [schema.core :as s]))

(defn- variant-names [vs]
  (reduce (fn [r [v _]] (conj r v)) [] vs))

(defn- variant-conditions [vs]
 (reverse
    (reduce
      (fn [r [v s]]
        (let [t (list 'fn '[x] (list '= '(first x) v))
              v (list 's/one (list 's/eq v) nil)
              s (if s [v s] [v])]
          (conj r t s)))
      '(s/conditional)
      vs)))

(defmacro variant-schema [& vs]
  (assert (even? (count vs)))
  (let [vs (partition 2 vs)
        n (variant-names vs)
        c (variant-conditions vs)]
    `(s/both
       (s/pred vector? 'is-a-vector)
       (s/pred #(keyword? (first %)) 'first-element-is-keyword)
       [(s/one (s/enum ~@n) 'valid-variant) s/Any]
       ~c)))
