(ns route-ccrs.schema.structure-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest]])
            #?(:cljs [cljs.test.check :refer [quick-check]])
            #?(:clj  [clojure.test.check.clojure-test :refer [defspec]]
               :cljs [cljs.test.check.cljs-test :refer-macros [defspec]])
            #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clojure.test.check.properties :as prop]
               :cljs [cljs.test.check.properties :as prop :include-macros true])
            [route-ccrs.generators.util :refer [gen-such-that]]
            [route-ccrs.generators.raw-part :as raw]
            [route-ccrs.generators.manufacturing-method :as mm]
            [route-ccrs.generators.structures.purchased :as ps]
            [route-ccrs.generators.structures.manufactured :as ms]
            [route-ccrs.schema.test-util
             :refer [is-valid-to-schema not-valid-to-schema]]
            [route-ccrs.schema.parts :refer [Structure
                                             StructureList
                                             StructuredItem
                                             valid-structure-in-use?]]))

(def gen-valid-structure (gen/one-of [(ps/gen-purch-struct) (ms/gen-valid)]))

(defspec structures-need-components
  (prop/for-all [s (gen/hash-map :id mm/gen-manufacturing-method)]
    (not-valid-to-schema Structure s)))

(defspec structure-ids-must-be-manufacturing-methods
  (prop/for-all [s (gen/hash-map :id gen/simple-type
                                 :components (gen/map
                                               gen/simple-type
                                               (raw/gen-raw-part)))]
                (not-valid-to-schema Structure s)))

(defspec invalid-manufacturing-methods-result-in-invalid-structures
  (prop/for-all [s (gen/hash-map :id (gen/fmap
                                       (fn [[m v]] (assoc m :type v))
                                       (gen/tuple
                                         mm/gen-manufacturing-method
                                         gen/simple-type))
                                 :components (gen/fmap
                                               (fn [v] {1 v})
                                               (raw/gen-raw-part)))]
                (not-valid-to-schema Structure s)))

(defspec structures-can-have-descriptions
  (prop/for-all [s (gen/fmap (fn [[s d]]
                               (assoc s :description d))
                             (gen/tuple gen-valid-structure gen/string-ascii))]
                (is-valid-to-schema Structure s)))

(defspec valid-structures-are-any-valid-subtype
  (prop/for-all [s gen-valid-structure]
                (is-valid-to-schema Structure s)))

(defspec valid-structure-lists-consist-of-zero-or-more-structures
  25
  (prop/for-all [l (gen/map gen/simple-type gen-valid-structure)]
                (is-valid-to-schema StructureList l)))

(deftest structure-list-keys-cannot-be-nil
  (not-valid-to-schema
    StructureList
    {nil (first (gen/sample (ps/gen-purch-struct) 1))}))

(defspec a-single-invalid-entry-invalidates-a-list
  (prop/for-all
    [l (gen/resize 5 (gen/map gen/simple-type gen-valid-structure))
     k gen/simple-type
     v gen/simple-type]
    (not-valid-to-schema StructureList (assoc l k v))))

(defspec any-struct-in-use-value-is-valid-if-it-matches-a-struct
  25
  (prop/for-all
    [l (gen/not-empty (gen/map gen/simple-type gen-valid-structure))]
    (is-valid-to-schema valid-structure-in-use?
                        {:structs l
                         :struct-in-use (rand-nth (keys l))})))

(deftest struct-in-use-must-exist-in-structs
  (let [s {1 (first (gen/sample gen-valid-structure 1))}]
    (not-valid-to-schema valid-structure-in-use? {:structs s :struct-in-use 2})
    (is-valid-to-schema valid-structure-in-use? {:structs s :struct-in-use 1})))

(defspec any-map-is-structured-if-it-has-structs
  25
  (prop/for-all
    [s (gen/not-empty
         (gen/resize
           5 (gen/map gen/simple-type gen-valid-structure)))
     m (gen/map gen/simple-type gen/simple-type)]
    (is-valid-to-schema
      StructuredItem
      (assoc m :structs s :struct-in-use (rand-nth (keys s))))))

(defspec maps-arent-structured-if-they-dont-have-structs
  25
  (prop/for-all
    [m (gen/map gen/simple-type gen/simple-type)
     s (gen/one-of
         [(gen/return {})
          (gen/hash-map :struct-in-use gen/simple-type)
          (gen/hash-map
            :structs
            (gen/resize 5 (gen/map gen/simple-type gen-valid-structure)))])]
    (not-valid-to-schema StructuredItem (merge m s))))
