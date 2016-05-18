(ns route-ccrs.schema.purchased-raw-part-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.ids.part-no-test :as pn]
            [route-ccrs.schema.part-sourcing-test :refer [gen-source]]
            [route-ccrs.schema.parts :refer [PurchasedRawPart]]))

(defn gen-raw-part
  ([] (gen-raw-part {}))
  ([{:keys [id type lead-time best-end-date source]
     :or {id pn/gen-part-no
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
     (gen-raw-part {:id pn/gen-invalid-part-no})
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

(defspec valid-raw-parts
  (prop/for-all [p (gen-raw-part)] (is-valid-to-schema PurchasedRawPart p)))

(defspec invalid-raw-parts
  (prop/for-all [p gen-invalid-raw-part]
                (not-valid-to-schema PurchasedRawPart p)))
