(ns route-ccrs.generators.structured-part
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            [route-ccrs.generators.util :as gu :refer [gen-such-that gen-id]]
            [route-ccrs.generators.part-no :as pn]
            [route-ccrs.generators.part-sourcing :refer [gen-source]]
            [route-ccrs.generators.structures.purchased :as ps]
            [route-ccrs.generators.structures.manufactured :as ms]))

(def ^:dynamic *sensible-child-list-size* 5)

(def gen-valid-structs
  (gen/not-empty
    (gen/resize
      *sensible-child-list-size*
      (gen/map
        gen-id
        (gen/one-of [(ps/gen-purch-struct) (ms/gen-valid)])))))

(def gen-valid-struct-attrs
  (gen/fmap
    (fn [structs]
      (let [struct-in-use (rand-nth (keys structs))]
        {:structs structs :struct-in-use struct-in-use}))
    gen-valid-structs))

(defn gen-structured-part
  ([] (gen-structured-part {}))
  ([{:keys [id type structs best-end-date source customer-part issue description]
     :or {id pn/gen-part-no
          type (gen/return :structured)
          customer-part gen/string-ascii
          issue gen/string-ascii
          description gen/string-ascii
          ; `:best-end-date` `nil`, or a valid `DateInst`
          best-end-date (gen/one-of [(gen/return nil) gu/gen-date])
          ; it *must* conform to the `StructuredItem` schema
          structs gen-valid-struct-attrs
          ; _optionally_ a `Sourced` record, containing a non-nil `:source`
          source (gen/one-of
                   [(gen/return {})
                    (gen/hash-map :source (gen-source :valid))])}}]
  (gen/fmap
    (partial apply merge)
    (gen/tuple
      (gen/hash-map
        :id id
        :type type
        :customer-part customer-part
        :issue issue
        :description description
        :best-end-date best-end-date)
      structs
      source))))

(def gen-invalid-structured-part
  (gen/one-of
    [; invalid id
     (gen-structured-part {:id pn/gen-invalid-part-no})
     ; invalid type
     (gen-structured-part {:type (gen/one-of
                                   [(gen/return :raw)
                                    (gen-such-that
                                      #(not= % :structured)
                                      gen/simple-type)])})
     ; invalid best end date
     (gen-structured-part {:best-end-date gen/simple-type})
     ; missing best end date
     (gen/fmap #(dissoc % :best-end-date) (gen-structured-part))
     ; invalid struct in use
     (gen/fmap
       (fn [[p k]]
         (assoc p :struct-in-use k))
       (gen-such-that
         (fn [[p k]] (not (contains? (:structs p) k)))
         (gen/tuple (gen-structured-part gen/simple-type))))
     ; invalid struct
     (gen/fmap
       (fn [[p k v]] (assoc-in p [:structs k] v))
       (gen/tuple
         (gen-structured-part)
         gen/simple-type
         gen/simple-type))
     ; extra fields
     (gu/gen-with-extra-fields
       (gen-structured-part)
       {:max *sensible-child-list-size*})]))

(defn gen-multilevel
  "Generates a sequence of structured parts and then recursively adds
  them to the component list of the `:struct-in-use` of the preceding
  part. Returns the top level part.

  If `valid?` is falsey then one random part will be invalid,
  invalidating the entire structure.

  For example, given a sequence of five structured parts:

      [part1 part2 part3 part4 part5]

  We would return:

      part1
        :structs[:struct-in-use]
          :components
            ::next-level => part2
              :structs[:struct-in-use]
                :components
                  ::next-level => part3
                    :structs[:struct-in-use]
                      :components
                        ::next-level => part4
                          :structs[:struct-in-use]
                            :components
                              ::next-level => part5

  Note that each part may have other structures and that the structures
  to which the `::next-level` part is added may have other components:
  they are fully fledged structured parts."
  ([] (gen-multilevel true))
  ([valid?]
   (gen/fmap
     #(first
        (reduce
          (fn [[r path] n]
            (let [s (rand-nth (keys (:structs n)))
                  np (concat path [:structs s :components ::next-level])
                  ; a little bit of fiddling to ensure that every level,
                  ; even if invalid, is still valid _enough_ that we can
                  ; actually add a component to it
                  n (if (associative? (get (:structs n) s))
                      n
                      (assoc-in n [:structs s] {}))]
              (if (nil? r)
                [n np]
                [(assoc-in r path n) np])))
          [nil []]
          %))
     (gen/fmap
       (fn [[vp ivp]] (if valid? vp (assoc vp (rand-int (count vp)) ivp)))
       (gen/tuple
         (gen/not-empty (gen/vector (gen-structured-part)))
          gen-invalid-structured-part)))))
