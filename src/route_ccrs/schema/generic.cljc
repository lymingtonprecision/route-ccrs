(ns route-ccrs.schema.generic
  (:require [schema.core :as s]))

(def int-gt-zero
  (s/both s/Int (s/pred pos? 'positive-integer)))

(def int-gte-zero
  (s/both s/Int (s/pred #(>= % 0) 'zero-or-positive-integer)))

(def num-gte-zero
  (s/both s/Num (s/pred #(>= % 0) 'zero-or-positive-number)))

(def asterisk-or-number
  (s/both s/Str (s/pred #(re-find #"^(\*|[1-9]+0*)$" %) 'asterisk-or-number)))

(def any-non-nil
  (s/pred (complement nil?) 'not-nil))
