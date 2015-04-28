(ns route-ccrs.generators.ccr
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util
             :refer [gen-such-that gen-with-extra-fields gen-double]]
            [route-ccrs.generators.work-center :as wc]
            [route-ccrs.generators.operation :as op]))

(def gen-valid-buffer
  (gen/one-of
    [gen/pos-int
     (gen-such-that #(>= % 0) gen-double)]))

(def gen-invalid-buffer
  (gen/one-of
   [(gen-such-that neg? gen/neg-int)
    (gen-such-that (complement number?) gen/simple-type)]))

(def gen-invalid-touch-time
  (gen/one-of
    [(gen-such-that (complement zero?) gen-double)
     (gen-such-that neg? gen/neg-int)
     (gen-such-that (complement number?) gen/simple-type)]))

(defn gen-ccr
  ([] (gen-ccr {}))
  ([{:keys [id operation total-touch-time pre-ccr-buffer post-ccr-buffer]
     :or {id wc/gen-valid-id
          operation op/gen-valid-id
          total-touch-time gen/pos-int
          pre-ccr-buffer gen-valid-buffer
          post-ccr-buffer gen-valid-buffer}}]
   (gen/hash-map
    :id id
    :operation operation
    :total-touch-time total-touch-time
    :pre-ccr-buffer pre-ccr-buffer
    :post-ccr-buffer post-ccr-buffer)))

(def gen-invalid-ccr
  (gen/one-of
    [; invalid id
     (gen-ccr {:id wc/gen-invalid-id})
     ; invalid operation
     (gen-ccr {:operation op/gen-invalid-id})
     ; invalid touch time
     (gen-ccr {:total-touch-time gen-invalid-touch-time})
     ; invalid buffers
     (gen-ccr {:pre-ccr-buffer gen-invalid-buffer})
     (gen-ccr {:post-ccr-buffer gen-invalid-buffer})
     ; extra keys
     (gen-with-extra-fields (gen-ccr))
     ; not a record
     gen/simple-type]))

