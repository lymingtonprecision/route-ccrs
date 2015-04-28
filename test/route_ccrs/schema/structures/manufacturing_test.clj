(ns route-ccrs.schema.structures.manufacturing-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.generators :as gen']
            [com.gfredericks.test.chuck.properties :as prop']
            [route-ccrs.generators.util :refer :all]
            [route-ccrs.generators.raw-part :as raw]
            [route-ccrs.generators.manufacturing-method :as mm]
            [route-ccrs.generators.route :as route]
            [route-ccrs.schema.test-util :refer :all]
            [route-ccrs.schema.parts :refer :all]))

(def ^:dynamic *sensible-child-list-size* 5)

(defspec route-types-match-structure-is-route-key-agnostic
  25
  (prop'/for-all
    [t gen/simple-type
     r (gen/map gen/simple-type (gen/return {:id {:type t}}))
     :let [s {:id {:type t} :routes r}]]
    (is-valid-to-schema route-types-match-structure? s)))

(defspec route-types-match-structure-fails-on-single-mismatch
  25
  (prop'/for-all
    [t gen/simple-type
     t2 (gen-such-that #(not= % t) gen/simple-type)
     r (gen/map gen/simple-type (gen/return {:id {:type t}}))
     :let [s {:id {:type t} :routes r}]]
    (not-valid-to-schema
      route-types-match-structure?
      (assoc-in s [:routes (rand-nth (keys (:routes s))) :id :type] t2))))

(defn gen-routed-attrs [t]
  (let [gen-id (route/gen-valid-id t)]
    (gen'/for [routes (gen/not-empty
                        (gen/resize
                          *sensible-child-list-size*
                          (gen/map
                            gen/simple-type
                            (route/gen-calculated-route {:id gen-id}))))
               :let [route-in-use (rand-nth (keys routes))]]
              {:routes routes
               :route-in-use route-in-use})))

(defn gen-valid
  ([] (gen-valid {}))
  ([{:keys [id components routes]
     :or {id (route/gen-valid-id :manufactured)
          components (gen/resize
                       *sensible-child-list-size*
                       (gen/map
                         gen/simple-type
                         (raw/gen-raw-part)))
          routes (gen-routed-attrs :manufactured)}}]
   (gen/fmap
     (partial apply merge)
     (gen/tuple
       (gen/hash-map
         :id id
         :components components)
       routes))))

(def gen-invalid-route-attrs
  (gen'/for
    [r (gen/not-empty (gen/map gen/simple-type gen/simple-type))
     :let [riu (rand-nth (keys r))]]
    {:routes r :route-in-use riu}))

(def gen-invalid
  (gen/one-of
    [; missing routes
     (gen-valid {:routes (gen/return {})})
     ; empty routes
     (gen-valid {:routes (gen/return {:routes {} :route-in-use nil})})
     ; invalid routes
     (gen-valid {:routes gen-invalid-route-attrs})
     ; nil components
     (gen-valid {:components (gen/return nil)})
     ; missing components
     (gen/fmap #(dissoc % :components) (gen-valid))
     ; invalid id
     (gen-valid {:id (route/gen-valid-id :invalid)})
     ; extra fields
     (gen-with-extra-fields
       (gen-valid)
       {:max *sensible-child-list-size*})]))

(defspec valid-single-level-manufactured-structure
  (prop/for-all [s (gen-valid)]
                (is-valid-to-schema ManufacturedStructure s)))

(defspec invalid-single-level-manufactured-structure
  (prop/for-all [s gen-invalid]
                (not-valid-to-schema ManufacturedStructure s)))

(defspec invalid-component-list-invalidates-structure
  *sensible-child-list-size*
  (prop/for-all
    [s (gen-valid
         {:components
          (gen/not-empty
            (gen/map
              gen/simple-type
              (gen/one-of [raw/gen-invalid-raw-part gen/simple-type])))})]
    (not-valid-to-schema ManufacturedStructure s)))
