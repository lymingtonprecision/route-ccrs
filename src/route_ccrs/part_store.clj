(ns route-ccrs.part-store
  "Provides a component, `IFSPartStore`, for retrieving part records
  from an IFS database.

  The component has a single dependency: a database connection, `:db`,
  that can be as the `db-spec` parameter in JDBC calls. Only one method
  is provided:

  * `get-part` which returns the record for a specific part.

  `get-part` returns part records matching the input schema of the other
  fns in this library."
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [yesql.core :refer [defquery]]
            [bugsbio.squirrel :as sq]
            [route-ccrs.sql.serializers :refer :all]
            [route-ccrs.schema.ids :as ids]
            [route-ccrs.schema.parts :as ps]
            [route-ccrs.schema.routes :as rs]
            [route-ccrs.manufacturing-methods :as mm]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

(s/defschema GetPartResult
  "A variant denoting the possible return values of `get-part`. Possible
  variants are:

  * `[:error {:invalid-part part-no}]`
  * `[:error {:id part-no <schema validation errors>}]`
  * `[:ok part-record]`"
  (s/either
    [(s/one (s/eq :ok) 'ok) (s/one ps/Part 'part)]
    [(s/one (s/eq :error) 'error)
     (s/one
       (s/either
         {:invalid-part ids/PartNo}
         {:id ids/PartNo
          s/Any s/Any})
       'validation-error)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocols

(defprotocol PartStore
  (get-part
    [this part-id]
    [this part-id recurse?]
    "Returns a `GetPartResult` as detailed below.

    If the requested part does not exist then
    `[:error {:invalid-part part-id}]` is returned.

    If the requested part, or a component part, does not validate to the
    schema then `[:error validation-error]` is returned where
    `validation-error` will be a map containing the `:id` of the
    requested part and the keys of those fields that are invalid. For
    example:

        [:error
         {:id \"100100002R01\"
          :structs
          {\"m1*\"
           {:components
            {\"100100001R01\"
             {:struct-in-use (not (not-nil nil))}}}}}]

    ... indicates that the component part `100100001R01`, in structure
    `m1*`, does not have any valid structures (and so cannot be used
    to build the parent part, invalidating it.)

    If the part exists and is valid then the result `[:ok part]` is
    returned with the `part` value containing the part record of the
    specified part with:

    * The part ID and its attributes.
    * If the part is structured, its structures including:
      * All of the routings for the structure.
      * All of the components with their structures/routings
        and, if `recurse?` is truthy (the default), their
        components on down the tree.

    To better understand the implications of the `recurse?` flag:

    With `recurse?` set to a truthy value:

    ```
    parent-part
      structure
        component-part
          structure
            component-part
            component-part
              structure
                component-part
                ...
    ```

    Whereas when it's not truthy:

    ```
    parent-part
      structure
        component-part
          structure
    ```
    "))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Database queries

(defquery -db-parts "route_ccrs/sql/parts.sql")
(defquery -db-structures "route_ccrs/sql/structures.sql")
(defquery -db-active-routes "route_ccrs/sql/active_routes.sql")
(defquery -db-structure-routes "route_ccrs/sql/structure_routes.sql")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Database (de-)serialization

(defn deserialize-part [r]
  (sq/to-clj r {:type keyword-serializer
                :lead-time int-serializer
                :best-end-date date-serializer}))

(defn deserialize-mm-id [r]
  (sq/to-clj r {:type keyword-serializer
                :revision int-serializer}))

(defn deserialize-structure [r]
  (let [r (sq/to-clj r {:type keyword-serializer
                        :revision int-serializer
                        :lead-time int-serializer
                        :best-end-date date-serializer})
        id (select-keys r [:type :revision :alternative])
        s {:id id :description (:description r)}]
    (if (= :purchased (:type id))
      (assoc s
             :lead-time (:lead-time r)
             :best-end-date (:best-end-date r))
      s)))

(defn deserialize-route-operation [r]
  (-> (sq/to-clj r {:route {:type keyword-serializer
                            :revision int-serializer}
                    :id int-serializer
                    :type keyword-serializer
                    :touch-time int-serializer
                    :buffer num-serializer
                    :potential-ccr bool-serializer})
      ((fn [x]
         {:route (:route x)
          :id (:id x)
          :description (:description x)
          :touch-time (:touch-time x)
          :buffer (:buffer x)
          :work-center {:id (:work-center x)
                        :description (:work-center-description x)
                        :type (:type x)
                        :hours-per-day (:hours-per-day x)
                        :potential-ccr? (:potential-ccr x)}}))))

(defn mm->sql-id-params [mm]
  {:bom_type (-> mm :id :type str (subs 1))
   :revision (-> mm :id :revision str)
   :alternative (-> mm :id :alternative)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Populate structures and routings

(s/defn ^:always-validate get-active-routes :- {s/Any rs/Route}
  "Returns a map of all currently active routings of the specified part."
  [db part :- {:id ids/PartNo s/Any s/Any}]
  (reduce
   (fn [r ro]
     (let [rid (-> ro :route (dissoc :description))
           rd (-> ro :route :description)
           k (mm/short-mm rid)
           o (dissoc ro :route)
           route (get r k {:id rid :description rd :operations []})]
       (assoc r k (update-in route [:operations] conj o))))
   {}
   (-db-active-routes {:part_no (:id part)}
                      {:connection db
                       :row-fn deserialize-route-operation})))

(s/defn ^:always-validate get-structure-route-ids :- [ids/ManufacturedMethodId]
  "Returns a collection of route IDs that can be used in combination
  with the provided structure."
  [db
   part :- {:id ids/PartNo s/Any s/Any}
   structure :- {:id ids/ManufacturedMethodId s/Any s/Any}]
  (-db-structure-routes (merge {:part_no (:id part)}
                               (mm->sql-id-params structure))
                        {:connection db
                         :row-fn deserialize-mm-id}))

(s/defn add-routes-to-structure
  "Given a part (id), structure (id), and map of routes; populates the
  route entries on the structure with those routes from the routes map
  with which it can be paired."
  [db
   part :- {:id ids/PartNo s/Any s/Any}
   structure :- {:id ids/ManufacturingMethod s/Any s/Any}
   routes :- {s/Str rs/Route}]
  (if (= :purchased (-> structure :id :type))
    structure
    (let [r (select-keys
             routes
             (map mm/short-mm (get-structure-route-ids db part structure)))
          riu (-> (map :id (vals r))
                  mm/preferred-mm
                  mm/short-mm)]
      (assoc structure :route-in-use riu :routes r))))

(s/defn get-component-parts :- {s/Any ps/Part}
  "Returns a map of the component parts for a given structure of a given
  part.

  If the component is a structured part then it will be passed to
  `populate-fn` to perform further processing such as retrieving
  its structures, routes, and own components. (Note: passing `identity`
  as the `populate-fn` would result in an invalid record because the
  part won't have any structure entries, so you need to provide a
  fn that accomplishes at least that.)"
  [db
   part :- {:id ids/PartNo s/Any s/Any}
   structure :- {:id ids/ManufacturingMethod s/Any s/Any}
   populate-fn]
  (reduce
   (fn [r c]
     (assoc r (:id c) (if (= :raw (:type c)) c (populate-fn c))))
   {}
   (-db-parts (merge {:part_no nil :parent_part_no (:id part)}
                     (mm->sql-id-params structure))
              {:connection db
               :row-fn deserialize-part})))

(s/defn populate-structures :- ps/StructuredPart
  [db, part :- {:id ids/PartNo s/Any s/Any}, with-descendants, recursive]
  (let [part-routes (get-active-routes db part)
        s (reduce
            (fn [r s]
              (let [c (if with-descendants
                        (get-component-parts
                          db part s
                          #(populate-structures db % recursive recursive))
                        {})
                    s (-> (add-routes-to-structure db part s part-routes)
                          (assoc :components c))]
                (assoc r (mm/short-mm (:id s)) s)))
            {}
            (-db-structures
              {:part_no (:id part)}
              {:connection db
               :row-fn deserialize-structure}))
        siu (mm/preferred-mm (map :id (vals s)))]
    (merge
      (select-keys part (map s/explicit-schema-key (keys ps/common-part-fields)))
      {:type :structured
       :best-end-date nil
       :struct-in-use (mm/short-mm siu)
       :structs s})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol implementation

(s/defn ^:always-validate -ifs-part :- GetPartResult
  ([part-store part] (-ifs-part part-store part true))
  ([part-store, part :- {:id ids/PartNo s/Any s/Any}, recurse?]
   (let [p (-db-parts {:part_no (:id part)
                       :parent_part_no nil
                       :bom_type nil
                       :revision nil
                       :alternative nil}
                      {:connection (:db part-store)
                       :row-fn deserialize-part
                       :result-set-fn first})
         p (if (= :structured (:type p))
             (populate-structures (:db part-store) p true recurse?)
             p)
         v (if p (s/check ps/Part p))]
     (cond
       (nil? p) [:error {:invalid-part (:id part)}]
       v [:error (assoc v :id (:id part))]
       :else [:ok p]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defrecord IFSPartStore [db]
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(extend IFSPartStore
  PartStore
  {:get-part -ifs-part})

(defn ifs-part-store
  "Creates and returns a new IFS Part Store component."
  []
  (component/using
   (map->IFSPartStore {})
   [:db]))
