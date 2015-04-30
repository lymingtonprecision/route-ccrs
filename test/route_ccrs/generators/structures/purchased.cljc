(ns route-ccrs.generators.structures.purchased
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util
             :refer [gen-such-that gen-date gen-with-extra-fields]]
            [route-ccrs.generators.raw-part :as raw]
            [route-ccrs.generators.manufacturing-method :as mm]))

(defn gen-purch-struct
  ([] (gen-purch-struct {}))
  ([{:keys [id components lead-time best-end-date]
     :or {id (gen/fmap #(assoc % :type :purchased) mm/gen-manufacturing-method)
          components (gen/return {})
          lead-time (gen-such-that pos? gen/pos-int)
          best-end-date gen-date}}]
   (gen/hash-map
     :id id
     :components components
     :lead-time lead-time
     :best-end-date best-end-date)))

(def gen-invalid-purch-struct
  (gen/one-of
    [; invalid method type
     (gen-purch-struct {:id (gen-such-that #(not= (:type %) :purchased)
                                           mm/gen-manufacturing-method)})
     ; invalid id
     (gen-purch-struct {:id mm/gen-invalid-manufacturing-method})
     ; nil components
     (gen/fmap #(assoc % :components nil) (gen-purch-struct))
     ; invalid lead time
     (gen-purch-struct {:lead-time
                        (gen/one-of
                          [(gen-such-that neg? gen/neg-int)
                           (gen-such-that (complement number?) gen/simple-type)])})
     ; invalid end date
     (gen-purch-struct {:best-end-date gen/simple-type})
     ; missing field
     (gen/fmap #(dissoc % (rand-nth (keys %))) (gen-purch-struct))
     ; invalid extra field
     (gen-with-extra-fields (gen-purch-struct))]))
