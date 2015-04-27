(ns route-ccrs.generators.work-center
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util :refer [gen-such-that gen-double]]))

(def work-center-types
  #{:internal :external})

(def gen-valid-id
  (gen-such-that
    (complement empty?)
    (gen/resize 5 gen/string-alphanumeric)))

(def gen-invalid-id
  (gen/one-of
   [(gen/return "")
    (gen-such-that #(> (count %) 5) gen/string-alphanumeric)
    (gen-such-that (complement string?) gen/simple-type)]))

(def gen-work-center
  (gen/hash-map
    :id gen-valid-id
    :description gen/string-ascii
    :type (gen/elements work-center-types)
    :hours-per-day (gen-such-that pos? (gen/one-of [gen-double gen/pos-int]))
    :potential-ccr? gen/boolean))
