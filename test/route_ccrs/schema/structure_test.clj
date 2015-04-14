(ns route-ccrs.schema.structure-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.purchased-raw-part-test :as raw]
            [route-ccrs.schema.ids.manufacturing-method-test :as mm]
            [route-ccrs.schema.structures.purchased-test :as ps]
            [route-ccrs.schema.structures.manufacturing-test :as ms]
            [route-ccrs.schema.parts :refer [Structure
                                             StructureList
                                             StructuredItem
                                             valid-structure-in-use?]]))

(def gen-valid-structure (gen/one-of [(ps/gen-valid) (ms/gen-valid)]))

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
    {nil (first (gen/sample (ps/gen-valid) 1))}))

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
