(ns route-ccrs.generators.util
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [is]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj [com.gfredericks.test.chuck.generators :as gen'])
            #?(:clj  [clj-time.core :as t]
               :cljs [cljs-time.core :as t])
            [schema.core :refer [check]]))

(def ^:dynamic *such-that-retries* 100)

(defn gen-with-extra-fields
  ([g] (gen-with-extra-fields g {}))
  ([g {:keys [max key-gen value-gen]
       :or {max nil
            key-gen gen/simple-type
            value-gen gen/simple-type}}]
   (let [exf (gen/not-empty (gen/map key-gen value-gen))]
     (if max
       (gen/resize max exf)
       exf))))

(def gen-date
  (gen/fmap #(t/plus (t/today) (t/days %)) gen/pos-int))

(defn gen-such-that
  ([pred gen] (gen-such-that pred gen *such-that-retries*))
  ([pred gen max-retries] (gen/such-that pred gen max-retries)))

#?(:clj  (def gen-double gen'/double)
   :cljs (def gen-double (gen/fmap #(rand %) gen/nat)))
