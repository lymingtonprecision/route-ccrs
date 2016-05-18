(ns route-ccrs.util.schema-dispatch
  "Helper for dispatching a multi-method based on Schemas.

  There are two public functions:

  * `get-schema` which returns the schema that can be used as a dispatch
    value within a multimethod for a given value.
  * `get-schema-method` which returns the dispatch fn of the schema
    that applies to a given value within a multimethod.

  Usage (of `get-schema`):

      (defmulti make-foo-bar
        (fn [x] (get-schema make-foo-bar x)))

      (defmethod make-foo-bar
        {:foo s/Int} [r] (assoc r :bar (* 2 (:foo r))))
      (defmethod make-foo-bar
        {:bar s/Int} [r] (assoc r :foo (/ (:bar r) 2)))
      (defmethod make-foo-bar
        {:foo s/Int :bar s/Int} [r] r)

      (make-foo-bar {:foo 5})         ;=> {:foo 5 :bar 10}
      (make-foo-bar {:bar 10})        ;=> {:foo 5 :bar 10}
      (make-foo-bar {:foo 3 :bar 16}) ;=> {:foo 3 :bar 16}

  This is particularly useful when combined with `defmethods` (from
  the broader `route-ccrs.util` namespace) and pre-defined (rather
  than in-line) schemas:

      (defmulti my-schema-dispatch
        (fn [x] (get-schema my-schema-dispatch x)))

      (defmethods my-schema-dispatch [x]
        Foo (process-foo x)
        Bar (finangle-bar x)
        Baz (interpolate-baz x))

  (The definition of the schemas and methods in the above are left to
  your imagination.)"
  (:require [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema determination

(defn matching-schema
  "Returns the schema `s` if a check of `m` against it succeeds,
  otherwise returns nil."
  [m s]
  (if-let [e (s/check s m)] nil s))

(defn multi-method-dispatch-schemas
  "Returns a coll of the schemas defined as dispatch values for the
  multi-method `mm`"
  [mm]
  (->> mm
       methods
       keys
       (filter #(satisfies? s/Schema %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn get-schema
  "Returns the schema to use for dispatching `v` in multi-method `mm`.

  If `v` has a `:schema` meta data entry then that is returned,
  otherwise returns the first schema defined as a dispatch value of
  `mm` against which a check of `v` succeeds.

  `nil` is returned if no matching schema can be found."
  [mm v]
  (if-let [s (-> v meta :schema)]
    s
    (some #(matching-schema v %) (multi-method-dispatch-schemas mm))))

(defn get-schema-method
  "Like `get-method` but for multimethods dispatching on schemas.

  Returns the dispatch fn, from `mm`, that applies to the schema that
  matches the dispatch value `v`, the default dispatch fn if no schemas
  apply, or `nil` if none apply and there is no default."
  [mm v]
  (get-method mm (get-schema mm v)))
