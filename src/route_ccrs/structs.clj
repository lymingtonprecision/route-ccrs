(ns route-ccrs.structs
  (:require [yesql.core :refer [defquery]]
            [route-ccrs.processing :refer :all]
            [route-ccrs.ccr :as ccr]
            [route-ccrs.routes :refer [route-id]]))

(defquery components-for-part "route_ccrs/sql/buildable_structures_for_part.sql")
(defquery route-ids-for-structure "route_ccrs/sql/routings_for_structure.sql")

(def structure-keys
  [:contract
   :part_no
   :eng_chg_level
   :bom_type
   :alternative_no])

(defn structure-id [r]
  (select-keys r structure-keys))

(def component-id-keys
  [:contract
   :component_part
   :component_bom_type
   :component_revision
   :component_alternate])

(defn component-id [r]
  (select-keys r component-id-keys))

(defn component-id->structure-id [i]
  (reduce
    (fn [r [x y]]
      (if-let [v (get i x)]
        (assoc r y v)
        r))
    {}
    [[:contract :contract]
     [:component_part :part_no]
     [:component_bom_type :bom_type]
     [:component_revision :eng_chg_level]
     [:component_alternate :alternative_no]]))

(defn assembly-id [struct-id route-id constrained-component]
  (merge
    {:ccc_part_no (:component_part constrained-component)
     :ccc_bom_type (:component_bom_type constrained-component)
     :ccc_eng_chg_level (:component_revision constrained-component)
     :ccc_structure_alternative (:component_alternate constrained-component)
     :ccc_routing_revision (:routing_revision_no constrained-component)
     :ccc_routing_alternative (:routing_alternative_no constrained-component)
     :ccc_best_end_date (:best_end_date constrained-component)
     :ccc_type (:component_type constrained-component)}
    route-id
    struct-id))

(defn transduce-structures
  "Transduces a list of structure components into tuples of
  `[structure-id components]` where `structure-id` is a map of the fields
  uniquely identifying the structure and `components` is a collection of
  the components that comprise the structure.

  Requires the component records to have a `:component_count` value
  signifying the number of components in the structure to which they
  belong. Otherwise no structures will be returned until the transducer
  is asked to complete and flush its state."
  [step]
  (transduce-by-shared-id structure-id #(vec []) :component_count step))

(defn deferred-structs [db part]
  (promise-transduced-query
    db
    (partial components-for-part part)
    transduce-structures
    {}))

(defn update-component-end-dates
  "Returns the collection up components updated with best end dates from
  `best-assembly-dates` where available"
  [components best-assembly-dates]
  (reduce
    (fn [r c]
      (let [id (-> c component-id component-id->structure-id)
            v (if-let [d (get best-assembly-dates id)]
                (merge c d)
                c)]
        (conj r v)))
    []
    components))

(defn select-constrained-component [components]
  (last (sort-by :best_end_date components)))

(defn select-valid-routes
  "Given a collection of routes returns the subset of them that are
  valid for use in combination with the specified structure."
  [db struct-id routes]
  (reduce
    (fn [vr id]
      (if-let [r (get routes (route-id id))]
        (conj vr [id r])
        vr))
    []
    (route-ids-for-structure struct-id {:connection db})))

(defn process-struct
  "For each valid method of manufacture (combination of a route from the
  `routes` given with the given structure) compares it's current CCR,
  as returned from the database, to that calculated from the current data
  and returns a tuple of `[structure-id best-end-dates database-updates]`.

  `best-end-dates` is a collection of maps denoting the routing used
  and the `:best_end_date` achieved.

  `database-updates` is a collection of variants of
  `[update-type update-fields]` where the `update-type` will be on of:
  `:insert`, `:update`, or `:replace` and `update-fields` is a map of
  the fields and their values required to perform the update."
  [db [struct-id components] routes best-assembly-dates]
  (let [dbc {:connection db}
        c (update-component-end-dates components best-assembly-dates)
        cc (select-constrained-component c)]
    (reduce
      (fn [[_ best-end db-updates] [route-id operations]]
        (let [assy-id (assembly-id struct-id route-id cc)
              c (ccr/get-last-known-struct-ccr (merge struct-id route-id) dbc)
              n (ccr/select-current-ccr (:best_end_date cc) operations dbc)
              u (ccr/ccr-entry-updates assy-id c n)]
          [struct-id
           (conj best-end (merge route-id n))
           (if (seq u)
             (conj db-updates u)
             db-updates)]))
      []
      (select-valid-routes db struct-id routes))))

(defn process-structs
  "Processes a collection of structures in parallel, passing each to
  `process-struct`, with the given `routes` and `best-assembly-dates`,
  and returning a tuple of `[best-end-dates database-updates]`.

  Unlike `process-struct` the returned `best-end-dates` is a map of
  the structure ID to the best end date _of the defaulting routing_,
  the other routings are dropped. (Note that the value is still a map
  containing the route details and the `:best_end_date`.)

  `database-updates` is a concatenation of the database updates returned
  from `process-struct`."
  [db structs routes best-assembly-dates]
  (reduce
    (fn [[bed su] [id be u]]
      (let [default-route-best-end (some #(if (= (int (:rank %)) 1) %) be)]
        [(if default-route-best-end (assoc bed id default-route-best-end) bed)
         (concat su u)]))
    [{} []]
    (parallel-process #(process-struct db % routes best-assembly-dates) structs)))

(defquery insert-history-entry! "route_ccrs/sql/insert_assembly_history.sql")
(defquery insert-current-ccr! "route_ccrs/sql/insert_assembly_ccr.sql")
(defquery update-current-ccr! "route_ccrs/sql/update_assembly_ccr.sql")
(defquery replace-current-ccr! "route_ccrs/sql/replace_assembly_ccr.sql")

(defn update! [db [t r]]
  (let [c {:connection db}]
    (insert-history-entry! r c)
    (cond
      (= :replace t) (replace-current-ccr! r c)
      (= :update t) (update-current-ccr! r c)
      (= :insert t) (insert-current-ccr! r c))))
