(ns route-ccrs.generators.shop-order
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util :refer [gen-such-that]]))

(def shop-order-no-re #"(?i)^(RMA|IFR|L)?\d+$")

(defn not-shop-order-no? [s]
  (nil? (re-find shop-order-no-re s)))

(def gen-shop-order-no
  (gen/fmap (partial apply str)
            (gen/tuple
              (gen/elements ["" "RMA" "IFR" "L"])
              (gen-such-that pos? gen/pos-int))))

(def gen-valid-release
  (gen/one-of
    [(gen/return "*")
     (gen/fmap str (gen-such-that pos? gen/pos-int))]))

(def gen-invalid-release
  (gen-such-that #(cond
                    (number? %) (< % 0)
                    (string? %) (nil? (re-find #"^(\*|\d+)$" %))
                    :else true)
                 gen/simple-type))

(defn gen-shop-order-id
  ([] (gen-shop-order-id {}))
  ([o r s] (gen-shop-order-id {:order-no o :release r :sequence s}))
  ([{:keys [order-no release sequence]
     :or {order-no gen-shop-order-no
          release gen-valid-release
          sequence gen-valid-release}}]
   (gen/hash-map :order-no order-no :release release :sequence sequence)))

(def gen-invalid-shop-order-id
  (gen/one-of
    [; invalid order number
     (gen-shop-order-id
       {:order-no
        (gen-such-that not-shop-order-no? gen/string-alphanumeric)})
     ; invalid release
     (gen-shop-order-id {:release gen-invalid-release})
     ; invalid sequence
     (gen-shop-order-id {:sequence gen-invalid-release})
     ; every field invalid
     (gen-shop-order-id
       (gen-such-that not-shop-order-no? gen/string-alphanumeric)
       gen-invalid-release
       gen-invalid-release)
     ; not a map
     gen/simple-type]))
