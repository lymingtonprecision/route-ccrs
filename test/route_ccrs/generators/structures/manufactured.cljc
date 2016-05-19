(ns route-ccrs.generators.structures.manufactured
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util
             :refer [gen-such-that gen-with-extra-fields gen-id]]
            [route-ccrs.generators.raw-part :as raw]
            [route-ccrs.generators.manufacturing-method :as mm]
            [route-ccrs.generators.route :as route]))

(def ^:dynamic *sensible-child-list-size* 5)

(defn gen-valid-routes [t]
  (let [gen-route-id (route/gen-valid-id t)]
    (gen/not-empty
      (gen/resize
        *sensible-child-list-size*
        (gen/map
          gen-id
          (route/gen-calculated-route {:id gen-route-id}))))))

(defn gen-routed-attrs [t]
  (let [route-gen (if (= :invalid t)
                    (gen/not-empty (gen/map gen/simple-type gen/simple-type))
                    (gen-valid-routes t))]
    (gen/fmap
      (fn [routes]
        (let [route-in-use (rand-nth (keys routes))]
          {:routes routes :route-in-use route-in-use}))
      route-gen)))

(def gen-invalid-route-attrs
  (gen-routed-attrs :invalid))

(defn gen-valid
  ([] (gen-valid {}))
  ([{:keys [id components routes]
     :or {id (route/gen-valid-id :manufactured)
          components (gen/resize
                       *sensible-child-list-size*
                       (gen/map
                         gen-id
                         (raw/gen-raw-part)))
          routes (gen-routed-attrs :manufactured)}}]
   (gen/fmap
     (partial apply merge)
     (gen/tuple
       (gen/hash-map
         :id id
         :components components)
       routes))))

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
