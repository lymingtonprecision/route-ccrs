(ns route-ccrs.generators.raw-part
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util
             :refer [gen-such-that gen-with-extra-fields gen-date]]
            [route-ccrs.generators.part-no :refer [gen-part-no gen-invalid-part-no]]
            [route-ccrs.generators.part-sourcing :refer [gen-source]]))

(defn gen-raw-part
  ([] (gen-raw-part {}))
  ([{:keys [id type lead-time best-end-date source]
     :or {id gen-part-no
          type (gen/return :raw)
          ; `:lead-time` zero, or a positive integer
          lead-time gen/pos-int
          ; `:best-end-date` `nil`, or a valid `Date`
          best-end-date (gen/one-of [(gen/return nil) gen-date])
          ; _optionally_ a `Sourced` record, containing a non-nil `:source`
          source (gen/one-of
                   [(gen/return nil)
                    (gen-source :valid)])}}]
   (gen/fmap
     (partial apply merge)
     (gen/tuple
       (gen/hash-map
         :id id
         :customer-part (gen/return nil)
         :issue (gen/return nil)
         :description gen/string-ascii
         :type type
         :lead-time lead-time
         :best-end-date best-end-date)
       (gen/fmap #(if (nil? %) {} {:source %}) source)))))

(def gen-invalid-raw-part
  (gen/one-of
    [; invalid id
     (gen-raw-part {:id gen-invalid-part-no})
     ; invalid type
     (gen-raw-part {:type (gen-such-that #(not= % :raw) gen/simple-type)})
     ; invalid lead time
     (gen-raw-part {:lead-time (gen-such-that neg? gen/neg-int)})
     ; invalid end date
     (gen-raw-part {:best-end-date
                    (gen-such-that (complement nil?) gen/simple-type)})
     ; invalid source
     (gen/one-of
      [(gen/fmap #(assoc % :source nil) (gen-raw-part))
       (gen-raw-part {:source (gen-source :invalid)})
       (gen-raw-part {:source gen/simple-type})])
     ; extra fields
     (gen-with-extra-fields (gen-raw-part))]))
