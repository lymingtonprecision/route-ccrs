(ns route-ccrs.part-zipper
  "Enables zipper traversal of a part structure.

  All standard zipper traversal and editing functionality is supported
  (see the tests) but `make-node` is only _assumed_ to work (no tests,
  nor expected use cases, nor trails; use at your own risk.)

  The traversal semantics are:

  * `down` on a structured part moves to the first structure list entry,
    `left` and `right` then move between structures.

  * `down` on a structure moves to a components/routes tuple, positioned
    on the component list entry from which `right` moves to the route
    list and `down` on either list moves to the first entry within it.

  * Raw parts and routes have no children, moving `down` from them
    returns `nil`.

  All list entries are returned as `{key value}` maps so you'll
  generally want to use the following pattern when editing them:

      (zip/edit (fn [n d] (assoc-in n (keys n) :best-end-date d)
                (java.util.Date.))
      (zip/edit (fn [n m] (update-in n (keys n) merge m))
                {:lead-time 20 :best-end-date (java.util.Date.)})

  (That is: use and `-in` fn with `(keys node)` as the path.)

  Similarly, when _adding_ nodes be aware that you _must_ supply them
  as `{key value}` maps:

      ; given a part zipper `z` of a structured part...
      (-> z
          down ; to the first structure
          down ; to the components/routes tuple
          insert-child {2 {...}}) ; insert a new component
  "
  (:require [clojure.zip :as zip]
            [schema.core :as s]
            [route-ccrs.schema.parts :refer [Part]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants

(def branching-keys
  "Which subkeys denote a branch in a part structure? These subkeys."
  [:structs :components :routes])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(defmacro recur-if
  "Recurs with the value of `expr` if truthy, otherwise returns `else`
  or `nil` if not provided."
  ([expr]
   `(recur-if ~expr nil))
  ([expr else]
   `(let [temp# ~expr]
      (if temp# (recur temp#) ~else))))

(defn branch?
  "Returns truthy if `x` contains a truthy value for one of the
  `branching-keys`."
  [x]
  (some #(get x %) branching-keys))

(defn branches
  "Returns a sequence of the `branching-keys` present in `x`, each
  branch is returned as a single element map of `{branching-key values}`.

  Note: if `x` is a purchased structure a phantom `:routes` branch is
  also returned to keep the traversal semantics the same as manufactured
  structures."
  [x]
  (let [b (reduce
           (fn [r k] (if-let [v (get x k)] (conj r {k v}) r))
           []
           branching-keys)]
    (if (and (seq b) (= (-> x :id :type) :purchased))
      (conj b {:routes {}})
      b)))

(defn branch-nodes
  "Returns the branch nodes that the zipper should traverse from `x`.

  When `x` has a single branch this is the sequence of items within that
  branch, each expressed as a single element map of `{key value}`.

  When `x` has multiple branches this is the sequence of those branches."
  [x]
  (let [b (branches x)
        n (count b)]
    (if (= n 1)
      (let [[_ nodes] (-> b first seq first)]
        (reduce
         (fn [r [k v]] (conj r {k v}))
         []
         nodes))
      b)))

(defn use-record-or-value
  "Returns a fn that calls `f` with it's arguments after potentially
  substituting the first argument with the value of it's first entry
  if it doesn't satisfy `branch?`

      (let [f (use-record-or-value #(clojure.pprint/pprint %))]
        (f {:structs {}})
        (f {1 {:components {} :routes {}}))
      ; prints:
      ; {:structs {}}
      ; {:components {} :routes {}}
  "
  [f]
  (fn [x & args]
    (if (branch? x)
      (apply f x args)
      (apply f (-> x vals first) args))))

(defn conservative-merge
  "Like merge but only merges keys that either exist in the left side
  map or aren't empty collections in the right side map."
  [& maps]
  (when (some identity maps)
    (reduce
     (fn [r m]
       (reduce
        (fn [r kv] (apply assoc r kv))
        r
        (filter
         (fn [[k v]] (or (contains? r k)
                         (not (coll? v))
                         (not-empty v)))
         m)))
     (first maps)
     (rest maps))))

(defn invalid-part-zipper-entry
  "Returns a message denoting the unexpected nature of `x` when
  encountered as a node within a part zipper."
  [x]
  (str "expected single element map, don't know how to zip a part entry like: "
       x))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn part-zipper
  "Returns a zipper for traversing a part structure."
  [part :- Part]
  (zip/zipper
   (use-record-or-value branch?)
   (use-record-or-value branch-nodes)
   (fn [node children]
     (assert (= 1 (count node)) (invalid-part-zipper-entry node))
     (let [k (if (:structs (first (vals node)))
               (conj (vec (keys node)) :structs)
               (keys node))
           v (if (some #(= (last k) %) branching-keys)
               (apply merge children)
               (apply conservative-merge (first (vals node)) children))]
       (assoc-in node k v)))
   {::part part}))

(defn root-part
  "Returns the part record at the root of a part-zipper.

  (Note: you should use this rather than `clojure.zipper/root` because
  we wrap the part before passing it to the zipper and this will undo
  that extra level of wrapping.)"
  [z]
  (::part (zip/root z)))
