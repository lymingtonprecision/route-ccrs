(ns route-ccrs.generators.manufacturing-method
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util
             :refer [gen-such-that gen-with-extra-fields]]))

(def ^:private valid-types [:manufactured :purchased :repair])

(def gen-type (gen/elements valid-types))
(def gen-revision (gen-such-that pos? gen/pos-int))
(def gen-alt (gen/fmap str (gen/frequency
                             [[5 (gen/return "*")]
                              [3 (gen/choose 1 9)]
                              [2 (gen/choose 10 100)]])))

(defn tuple->manufacturing-method [[m r a]]
  {:type m :revision r :alternative a})

(def gen-manufacturing-method
  (gen/fmap
    tuple->manufacturing-method
    (gen/tuple gen-type gen-revision gen-alt)))

(def gen-mm-with-invalid-type
  (gen/fmap
    tuple->manufacturing-method
    (gen/tuple
      (gen-such-that #(nil? (% valid-types)) gen/keyword)
      gen-revision
      gen-alt)))

(def gen-mm-with-invalid-rev
  (gen/fmap
    tuple->manufacturing-method
    (gen/tuple
      gen-type
      (gen/one-of [gen/neg-int
                   (gen/return nil)
                   (gen-such-that (complement number?) gen/simple-type)])
      gen-alt)))

(def gen-mm-with-invalid-alt
  (gen/fmap
    tuple->manufacturing-method
    (gen/tuple
      gen-type
      gen-revision
      (gen/one-of [(gen/return "0")
                   (gen/return nil)
                   (gen/fmap str gen/neg-int)
                   (gen-such-that
                     #(nil? (re-find #"^(\*|[1-9]\d+)$" %))
                     gen/string)
                   (gen-such-that (complement string?) gen/simple-type)]))))

(def gen-mm-with-extra-keys
  (gen-with-extra-fields gen-manufacturing-method {:max 5}))

(def gen-invalid-manufacturing-method
  (gen/one-of
    [gen-mm-with-extra-keys
     gen-mm-with-invalid-type
     gen-mm-with-invalid-alt
     gen-mm-with-invalid-rev]))
