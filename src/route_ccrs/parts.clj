(ns route-ccrs.parts
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [yesql.core :refer [defquery]]
            [bugsbio.squirrel :as sq]
            [route-ccrs.sql.serializers :refer :all]
            [route-ccrs.schema.ids :as ids]
            [route-ccrs.schema.parts :as ps]
            [route-ccrs.schema.routes :as rs]
            [route-ccrs.manufacturing-methods :as mm]

            ; old deps
            [clojure.core.async :as async]
            [route-ccrs.processing :refer :all]
            [route-ccrs.routes :as route]
            [route-ccrs.structs :as struct]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocols

(defprotocol PartStore
  (active-parts
    [this]
    "Returns a collection of the currently active parts in this data
    store, each part is represented by an `ActivePart` record.")
  (get-part
    [this part-id]
    [this part-id recurse?]
    "Returns the part record of the specified part with:

    * The part ID and its attributes.
    * If the part is structured, its structures including:
      * All of the routings for the structure.
      * All of the components with their structures/routings
        and, if `recurse?` is truthy, their components on down
        the tree.

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

(defquery -db-active-parts "route_ccrs/sql/active_parts.sql")
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
        s {:id id}]
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
                    :potential-ccr bool-serializer})
      ((fn [x]
         {:route (:route x)
          :id (:id x)
          :touch-time (:touch-time x)
          :work-center {:id (:work-center x)
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
      (let [rid (:route ro)
            k (mm/short-mm rid)
            o (dissoc ro :route)
            route (get r k {:id rid :operations []})]
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

(s/defn ^:always-validate get-component-parts :- {s/Any ps/Part}
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

(s/defn ^:always-validate populate-structures :- ps/StructuredPart
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
    {:id (:id part)
     :type :structured
     :best-end-date nil
     :struct-in-use (mm/short-mm siu)
     :structs s}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol implementation

(s/defn ^:always-validate -ifs-active-parts :- [ps/ActivePart]
  [part-store]
  (-db-active-parts
    {}
    {:connection (:db part-store)
     :row-fn #(sq/to-clj % {:low-level-code int-serializer})}))

(s/defn ^:always-validate -ifs-part :- (s/maybe ps/Part)
  ([part-store part] (-ifs-part part-store part true))
  ([part-store, part :- {:id ids/PartNo s/Any s/Any}, recurse?]
   (let [p (-db-parts {:part_no (:id part)
                       :parent_part_no nil
                       :bom_type nil
                       :revision nil
                       :alternative nil}
                      {:connection (:db part-store)
                       :row-fn deserialize-part
                       :result-set-fn first})]
     (if (= :raw (:type p))
       p
       (populate-structures (:db part-store) p true recurse?)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defrecord IFSPartStore [db])

(extend IFSPartStore
  PartStore
  {:active-parts -ifs-active-parts
   :get-part -ifs-part})

(defn ifs-part-store []
  (component/using
    (map->IFSPartStore {})
    [:db]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; On the chopping block

(defn process-part
  "Calculates the best end dates/CCRs for the active structures and routings
  of the given part.

  Returns a tuple of `[best-end-dates route-ccr-updates structure-ccr-updates]`
  where `best-end-dates` is a map of structure IDs to records that contain, at
  least, the calculated best end date when using the default routing, and the
  two `-updates` elements are collections of database update records reflecting
  any changes from the current state.

  Takes, as arguments:

  * `db` a JDBC database specification
  * `part` a part record
  * `best-assembly-dates`, optional, a map of structure IDs to records that
    contain their best end dates. Used as a lookup for the availability dates
    of components (the `best-end-dates` return value of this `fn` matches this
    format, to enable recursive calls and processing BOMs bottom up)"
  ([db part] (process-part db part {}))
  ([db part best-assembly-dates]
   (let [routes (route/deferred-routes db part)
         structs (struct/deferred-structs db part)
         route-updates (promise)
         _ (async/go
             (let [u (route/process-routes db @routes)]
               (deliver route-updates u)))
         best-end-dates (promise)
         struct-updates (promise)
         _ (async/go
             (let [[b u] (struct/process-structs
                           db @structs @routes best-assembly-dates)]
               (deliver best-end-dates b)
               (deliver struct-updates u)))]
     [@best-end-dates @route-updates @struct-updates])))

(defn process-parts
  "Processes a collection of parts in parallel, passing each to `process-part`
  and returning a concatenation of the results."
  ([db parts] (process-parts db parts {}))
  ([db parts best-assembly-dates]
   (reduce
     (fn [[bed ru su] [b r s]]
       [(merge bed b) (concat ru r) (concat su s)])
     [{} [] []]
     (parallel-process #(process-part db % best-assembly-dates) parts))))

(defn process-part-batches
  "Processes a collection of collections of parts, passing each batch of parts
  to `process-parts` _with the `best-end-dates` of all preceding batches_ and
  returning a concatenation of the results."
  [db batches]
  (reduce
    (fn [[bed ru su] parts]
      (let [[b r s] (process-parts db parts bed)]
        [(merge bed b)
         (concat ru r)
         (concat su s)]))
    [{} [] []]
    batches))
