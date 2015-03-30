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

  All list entries are returned as `{key value}` maps and there are
  three utility fns to make working with them in this format easier:

      (let [p {:id \"100104678R01\"
               :type :structured
               :best-end-date nil
               :struct-in-use 1
               :structs
               {1 {:id {:type :purchased :revision 1 :alternative "*"}
                  :lead-time 10 :best-end-date nil :components {}}}}
            z (part-zipper p)
            loc (-> z down)]
        (zip/node loc)   ;=> {1 {:id {...} ...}}
        (node-key loc)   ;=> 1
        (node-val loc) ;=> {:id {...} ...}}

        (edit-val loc assoc :best-end-date (java.util.Date.))
        ;=> zipper loc with the node now equal to:
        ;=> {1 {:id {:type :purchased :revision 1 :alternative "*"}
        ;=>     :lead-time 10 :components {}
        ;=>     :best-end-date #inst \"2015-03-30T00:00:00.00\"}

        (root-part z))
        ;=> {:id \"100104678R01\"
        ;=>  :type :structured
        ;=>  :best-end-date nil
        ;=>  :struct-in-use 1
        ;=>  :structs
        ;=>  {1 {:id {:type :purchased :revision 1 :alternative "*"}
        ;=>     :lead-time 10
        ;=>     :best-end-date #inst \"2015-03-30T00:00:00.00\"
        ;=>     :components {}}}}

  A `root-part` fn is also provided as the part supplied to the zipper
  undergoes some extra wrapping which will be left in place if you use
  the standard `zip/root` fn. This takes care of that extra wrapping
  whilst also doing everything `zip/root` does.

  Be aware that when _adding_ nodes you **must** supply them as
  `{key value}` maps:

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

(defn take-until
  "Like `take-while` but includes the item that matches `pred`."
  [pred coll]
  (let [[t r] (split-with (complement pred) coll)]
    (if-let [x (first r)]
      (conj (vec t) x)
      (vec t))))

(defn ^:private -node-key [loc]
  (-> loc zip/node keys first))

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

(defn node-key
  "Returns the key of the node at `loc`."
  [loc]
  (let [k (-node-key loc)]
    (if (not= ::part k) k)))

(defn node-val
  "Returns the value of the node at `loc`."
  [loc]
  (-> loc zip/node vals first))

(defn edit-val
  "Replaces the node at `loc` with the value of `(f node-value args)`,
  keeping its key."
  [loc f & args]
  (let [k (-node-key loc)
        v (apply f (node-value loc) args)]
    (zip/replace loc {k v})))

(defn path-from-loc-to-part
  "Returns the path from the zipper location `loc` to the part to which
  it belongs.

  If `loc` is a part then a sequence containing just the part number is
  returned.

  If `loc` is a routing or structure then a sequence of IDs leading back
  to the part to which it belongs is returned:

     [route-id struct-id part-id]
     ; or
     [struct-id part-id]
  "
  [loc]
  (let [path-to-item (if-let [p (zip/path loc)]
                       (->> (map #(-> % vals first :id) p)
                            (remove nil?)
                            reverse)
                       [])
        full-path (conj path-to-item (-> loc zip/node vals first :id))]
    (vec (take-until string? full-path))))

(defn path-from-part-to-loc
  "A convenience for reversing the results of `path-from-loc-to-part`."
  [loc]
  (-> loc path-from-loc-to-part reverse vec))
