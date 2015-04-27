(ns route-ccrs.generators.operation
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util :refer [gen-such-that gen-double]]
            [route-ccrs.generators.work-center :as wc]))

(def gen-valid-id (gen-such-that pos? gen/pos-int))
(def gen-invalid-id (gen/one-of
                     [gen/neg-int
                      gen-double
                      (gen-such-that #(if (number? %) (< % 1) true)
                                     gen/simple-type)]))

(defn gen-operation
  ([] (gen-operation {}))
  ([{:keys [id touch-time work-center description]
     :or {id gen-valid-id
          description gen/string-ascii
          touch-time gen/pos-int
          work-center wc/gen-work-center}}]
   (gen/hash-map
    :id id
    :description description
    :touch-time touch-time
    :work-center work-center)))

(def gen-invalid-operation
  (gen/one-of
   [; invalid id
    (gen-operation {:id gen-invalid-id})
     ; invalid work center
    (gen-operation {:work-center gen/simple-type})
     ; invalid touch time
    (gen-operation {:touch-time
                    (gen/one-of
                     [(gen-such-that neg? gen/neg-int)
                      (gen-such-that (complement zero?) gen-double)
                      (gen-such-that (complement number?) gen/simple-type)])})
     ; not a record
    gen/simple-type]))
