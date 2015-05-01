(ns route-ccrs.part-zipper-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [use-fixtures deftest is]])
            #?(:clj [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [clj-time.core :as t]
               :cljs [cljs-time.core :as t])
            #?(:cljs [cljs-time.extend])
            [schema.test]
            [route-ccrs.generators.raw-part :refer [gen-raw-part]]
            [clojure.zip :as zip :refer [up down left right node]]
            [route-ccrs.part-zipper :as pz]))

(use-fixtures :once schema.test/validate-schemas)

(def MC032
  {:id "MC032" :type :internal :hours-per-day 8 :potential-ccr? true})

(def simple-test-part
  {:id "100102347R01"
   :type :structured
   :best-end-date nil
   :struct-in-use 1
   :structs
   {1 {:id {:type :purchased :revision 1 :alternative "*"}
       :lead-time 10
       :best-end-date nil
       :components
       {1 {:id "100112049R02"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :manufactured :revision 1 :alternative "1"}
               :route-in-use 1
               :routes
               {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                   :operations [{:id 10 :work-center MC032 :touch-time 10}]}}
               :components
               {1 {:id "100120035R01"
                   :type :raw
                   :lead-time 5
                   :best-end-date nil}
                2 {:id "100106687R02"
                   :type :raw
                   :lead-time 15
                   :best-end-date nil}}}}}}}}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; root-part

(deftest root-part-returns-part-at-root-of-zipper
  (let [p simple-test-part
        z (pz/part-zipper p)]
    (is (= p (-> z down down down down down pz/root-part)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; format of nodes

(deftest struct-nodes-are-single-element-maps-of-the-struct-entry
  (let [p simple-test-part
        z (pz/part-zipper p)]
    (is (= (-> z down node) {1 (-> p :structs (get 1))}))))

(deftest component-nodes-are-single-element-maps-of-the-component-entry
  (let [rp (first (gen/sample (gen-raw-part) 1))
        p (assoc-in simple-test-part [:structs 1 :components 2] rp)
        z (pz/part-zipper p)]
    (is (= (set (reduce
                 (fn [r [k v]] (conj r {k v}))
                 []
                 (get-in p [:structs 1 :components])))
           (-> z down down zip/children set)))))

(deftest route-nodes-are-single-element-maps-of-the-route-entry
  (let [p simple-test-part
        z (pz/part-zipper p)]
    (is (= (set (reduce
                 (fn [r [k v]] (conj r {k v}))
                 []
                 (get-in p [:structs 1 :components 1 :structs 1 :routes])))
           (-> z down down down down down right zip/children set)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; node utility fns

(deftest node-key-of-the-root-is-nil
  (is (nil? (pz/node-key (pz/part-zipper simple-test-part)))))

(deftest node-keys
  (let [p {:id "100105468R01"
           :type :structured
           :best-end-date nil
           :struct-in-use 1
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10 :best-end-date nil :components {}}
            :a {:id {:type :purchased :revision 1 :alternative "1"}
                :lead-time 10 :best-end-date nil :components {}}
            "m" {:id {:type :manufactured :revision 1 :alternative "*"}
                 :components
                 {[:a 1 '*] {:id "100104687R74"
                             :type :raw :lead-time 10 :best-end-date nil}}
                 :route-in-use 'ow
                 :routes
                 {'ow {:id {:type :manufactured :revision 1 :alternative "*"}
                       :operations [{:id 10 :work-center MC032 :touch-time 1}]}}}}}
        z (pz/part-zipper p)]
    (is (= 1 (-> z down pz/node-key)))
    (is (= :components (-> z down down pz/node-key)))
    (is (= :a (-> z down right pz/node-key)))
    (is (= [:a 1 '*] (-> z down zip/rightmost down down pz/node-key)))
    (is (= 'ow (-> z down zip/rightmost down right down pz/node-key)))))

(deftest node-val-of-the-root-is-the-supplied-part
  (is (= simple-test-part (pz/node-val (pz/part-zipper simple-test-part)))))

(deftest node-vals
  (let [p simple-test-part
        z (pz/part-zipper p)]
    (is (= (get-in p [:structs 1]) (-> z down pz/node-val)))
    (is (= (get-in p [:structs 1 :components 1])
           (-> z down down down pz/node-val)))
    (is (= (get-in p [:structs 1 :components 1 :structs 1 :routes 1])
           (-> z down down down down down right down pz/node-val)))
    (is (= (get-in p [:structs 1 :components 1 :structs 1 :components 2])
           (-> z down down down down down down right pz/node-val)))))

(deftest edit-vals
  (let [p simple-test-part
        f {:best-end-date (t/today)
           :ccr nil
           :ccr-queue 0
           :total-touch-time 10
           :total-buffer 5}
        z (pz/part-zipper p)]
    (is (= (assoc p :best-end-date (:best-end-date f))
           (-> z (pz/edit-val assoc :best-end-date (:best-end-date f))
               pz/root-part)))
    (is (= (update-in p [:structs 1 :components 1 :structs 1 :routes 1]
                      merge f)
           (-> z down down down down down right down
               (pz/edit-val merge f)
               pz/root-part)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/children and zip/branch?

(deftest top-level-traversal
  (let [p simple-test-part
        z (pz/part-zipper p)]
    (is (nil? (zip/left z)))
    (is (nil? (zip/right z)))
    (is (= #{:components :routes}
           (->> z zip/down zip/children (map #(-> % keys first)) set)))))

(deftest all-structs-are-children-of-a-structured-part
  (let [p {:id "100105468R01"
           :type :structured
           :best-end-date nil
           :struct-in-use 3
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10 :best-end-date nil :components {}}
            2 {:id {:type :purchased :revision 1 :alternative "1"}
               :lead-time 10 :best-end-date nil :components {}}
            3 {:id {:type :purchased :revision 2 :alternative "*"}
               :lead-time 10 :best-end-date nil :components {}}
            4 {:id {:type :manufactured :revision 1 :alternative "*"}
               :components {}
               :route-in-use 1
               :routes
               {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                   :operations [{:id 10 :work-center MC032 :touch-time 1}]}}}}}
        z (pz/part-zipper p)
        zs (zip/children z)]
    (is (= (count zs) (count (set zs))))
    (is (= (set zs)
           (set (->> p :structs (reduce (fn [r [k v]] (conj r {k v})) [])))))))

(deftest struct-traversal
  (let [p simple-test-part
        z (pz/part-zipper p)]
    (is (= (-> z down node) {1 (get-in p [:structs 1])}))
    (is (= (-> z down down down node) {1 (get-in p [:structs 1 :components 1])}))
    (is (= (-> z down down down down down right down node)
           {1 (get-in p [:structs 1
                         :components 1
                         :structs 1
                         :routes 1])}))))

(deftest structs-without-components-still-branch-to-that-level
  (let [childless-part (assoc-in simple-test-part [:structs 1 :components] {})
        z (pz/part-zipper childless-part)]
    (is (zip/branch? (-> z down down)))))

(deftest routes-have-no-children
  (let [p (get-in simple-test-part [:structs 1 :components 1])
        r {1 (get-in p [:structs 1 :routes 1])}
        z (pz/part-zipper p)
        rn (-> z down down right down)]
    (is (= (node rn) r))
    (is (not (zip/branch? rn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/edit

(deftest editing-structure-and-component-end-dates
  (let [d (t/today)
        p simple-test-part
        z (pz/part-zipper p)
        update-best-end-date (fn [n d]
                               (assoc-in n
                                         (conj (vec (keys n)) :best-end-date)
                                         d))
        ep (-> z down down down
               (zip/edit update-best-end-date d)
               up
               up
               (zip/edit update-best-end-date d)
               pz/root-part)]
    (is (= ep
           (-> p
               (assoc-in [:structs 1 :components 1 :best-end-date] d)
               (assoc-in [:structs 1 :best-end-date] d))))))

(deftest editing-a-routing
  (let [d (t/today)
        p simple-test-part
        e {:best-end-date d
           :ccr MC032
           :ccr-queue 0
           :total-touch-time 10
           :total-buffer 5}
        z (pz/part-zipper p)
        ep (-> z down down down down down right down
               (zip/edit (fn [n m] (update-in n (keys n) merge m)) e)
               pz/root-part)]
    (is (= ep
           (update-in p [:structs 1 :components 1 :structs 1 :routes 1]
                      merge e)))))

(deftest using-a-loop-to-adjust-all-end-dates
  (let [d (t/today)
        paths [[:best-end-date]
               [:structs 1 :best-end-date]
               [:structs 1 :components 1 :best-end-date]
               [:structs 1 :components 1 :structs 1 :components 1 :best-end-date]
               [:structs 1 :components 1 :structs 1 :components 2 :best-end-date]]
        set-end-dates (fn [p dates]
                        (reduce
                         (fn [r [path date]] (assoc-in r path date))
                         p
                         (partition 2 (interleave paths dates))))
        p (set-end-dates simple-test-part (repeat (count paths) d))
        i (repeatedly (count paths) #(rand-int (* 365 2)))
        e (set-end-dates p (map #(t/plus d (t/days %)) i))]
    (is (= e
           (loop [z (pz/part-zipper p) incs i]
             (if (zip/end? z)
               (pz/root-part z)
               (let [[n incs] (if (:best-end-date (-> z zip/node vals first))
                                [(zip/edit z
                                           (fn [n i]
                                             (update-in n (conj (vec (keys n))
                                                                :best-end-date)
                                                        #(t/plus % (t/days i))))
                                           (first incs))
                                 (rest incs)]
                                [z incs])]
                 (recur (zip/next n) incs))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/replace

(deftest replacing-a-calculated-route-with-an-uncalculated-route
  (let [d (t/today)
        ep simple-test-part
        r (get-in ep [:structs 1 :components 1 :structs 1 :routes 1])
        c {:best-end-date d
           :ccr {:id (:id MC032)
                 :operation 10
                 :total-touch-time 5
                 :pre-ccr-buffer 1
                 :post-ccr-buffer 5}
           :ccr-queue 0
           :total-touch-time 10
           :total-buffer 5}
        p (update-in ep [:structs 1 :components 1 :structs 1 :routes 1]
                     merge c)
        z (pz/part-zipper p)]
    (is (= ep
           (-> z down down down down down right down
               (zip/replace {1 r})
               pz/root-part)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/remove

(deftest removing-a-structure
  (let [p {:id "100105468R01"
           :type :structured
           :best-end-date nil
           :struct-in-use 3
           :structs
           {1 {:id {:type :purchased :revision 1 :alternative "*"}
               :lead-time 10 :best-end-date nil :components {}}
            2 {:id {:type :purchased :revision 1 :alternative "1"}
               :lead-time 10 :best-end-date nil :components {}}
            3 {:id {:type :purchased :revision 2 :alternative "*"}
               :lead-time 10 :best-end-date nil :components {}}
            4 {:id {:type :manufactured :revision 1 :alternative "*"}
               :components {}
               :route-in-use 1
               :routes
               {1 {:id {:type :manufactured :revision 1 :alternative "*"}
                   :operations [{:id 10 :work-center MC032 :touch-time 1}]}}}}}
        z (pz/part-zipper p)
        k (rand-nth (-> p :structs keys))]
    (is (= (update-in p [:structs] dissoc k)
           (->> z down
                (#(if (= (keys (zip/node %)) [k]) % (recur (zip/right %))))
                zip/remove
                pz/root-part)))))

(deftest removing-a-component
  (let [p simple-test-part
        z (pz/part-zipper p)
        k 2]
    (is (= (update-in p [:structs 1 :components 1 :structs 1 :components]
                      dissoc
                      k)
           (->> z down down down down down down
                (#(if (= (keys (zip/node %)) [k]) % (zip/right %)))
                zip/remove
                pz/root-part)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/append-child

(deftest appending-a-structure
  (let [p simple-test-part
        k 2
        v {:id {:type :purchased :revision 1 :alternative "2"}
           :lead-time 10 :best-end-date nil :components {}}
        z (pz/part-zipper p)]
    (is (= (update-in p [:structs] assoc k v)
           (-> z (zip/append-child {k v}) pz/root-part)))))

(deftest appending-a-component
  (let [c (first (gen/sample (gen-raw-part)))
        p simple-test-part
        z (pz/part-zipper p)
        ep (-> z down down
               (zip/append-child {2 c})
               pz/root-part)]
    (is (= (assoc-in p [:structs 1 :components 2] c) ep))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/insert-child

(deftest inserting-a-child-structure
  (let [p simple-test-part
        k 2
        v {:id {:type :purchased :revision 1 :alternative "2"}
           :lead-time 10 :best-end-date nil :components {}}
        z (pz/part-zipper p)]
    (is (= (update-in p [:structs] assoc k v)
           (-> z (zip/insert-child {k v}) pz/root-part)))))

(deftest inserting-a-child-component
  (let [c (first (gen/sample (gen-raw-part)))
        p simple-test-part
        z (pz/part-zipper p)
        ep (-> z down down
               (zip/insert-child {2 c})
               pz/root-part)]
    (is (= (assoc-in p [:structs 1 :components 2] c) ep))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/insert-left (and, effectively, zip/insert-right)

(deftest inserting-a-sibling-structure
  (let [p simple-test-part
        k 2
        v {:id {:type :purchased :revision 1 :alternative "2"}
           :lead-time 10 :best-end-date nil :components {}}
        z (pz/part-zipper p)]
    (is (= (update-in p [:structs] assoc k v)
           (-> z down (zip/insert-left {k v}) pz/root-part)))))

(deftest inserting-a-sibling-component
  (let [c (first (gen/sample (gen-raw-part)))
        p simple-test-part
        z (pz/part-zipper p)
        ep (-> z down down down
               (zip/insert-left {2 c})
               pz/root-part)]
    (is (= (assoc-in p [:structs 1 :components 2] c) ep))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; path-from-loc-to-part

(deftest path-to-the-root-part-is-the-part-number
  (let [p simple-test-part
        z (pz/part-zipper p)]
    (is (= [(:id p)] (vec (pz/path-from-loc-to-part z))))
    (is (= [(:id p)] (vec (pz/path-from-loc-to-root z))))))

(deftest path-from-component-part
  (let [p simple-test-part
        z (pz/part-zipper p)
        l (-> z down down down)
        ep [(get-in p [:structs 1 :components 1 :id])]]
    (is (= ep (vec (pz/path-from-loc-to-part l))))
    (is (= (into ep [(get-in p [:structs 1 :id]) (:id p)])
           (vec (pz/path-from-loc-to-root l))))))

(deftest path-from-component-struct
  (let [p simple-test-part
        z (pz/part-zipper p)
        l (-> z down down down down)
        ep [(get-in p [:structs 1 :components 1 :structs 1 :id])
            (get-in p [:structs 1 :components 1 :id])]]
    (is (= ep (vec (pz/path-from-loc-to-part l))))
    (is (= (into ep [(get-in p [:structs 1 :id]) (:id p)])
           (vec (pz/path-from-loc-to-root l))))))

(deftest path-from-component-route
  (let [p simple-test-part
        z (pz/part-zipper p)
        l (-> z down down down down down right down)
        ep [(get-in p [:structs 1 :components 1 :structs 1 :routes 1 :id])
            (get-in p [:structs 1 :components 1 :structs 1 :id])
            (get-in p [:structs 1 :components 1 :id])]]
    (is (= ep (vec (pz/path-from-loc-to-part l))))
    (is (= (into ep [(get-in p [:structs 1 :id]) (:id p)])
           (vec (pz/path-from-loc-to-root l))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip movement and end? fns exist as a convenience

(deftest zip-movement-fns-exist-as-a-convenience
  (let [p (get-in simple-test-part [:structs 1 :components 1])
        z (pz/part-zipper p)]
    (is (= (zip/down z) (pz/down z)))
    (is (= (-> z zip/down zip/up) (-> z pz/down pz/up)))
    (is (= (-> z zip/down zip/down zip/right) (-> z pz/down pz/down pz/right)))
    (is (= (-> z zip/down zip/down zip/left) (-> z pz/down pz/down pz/left)))
    (is (= (-> z zip/down zip/down zip/down zip/leftmost)
           (-> z pz/down pz/down pz/down pz/leftmost)))
    (is (= (-> z zip/down zip/down zip/down zip/rightmost)
           (-> z pz/down pz/down pz/down pz/rightmost)))
    (is (= (-> z zip/down zip/next) (-> z pz/down pz/next)))
    (is (= (-> z zip/down zip/prev) (-> z pz/down pz/prev)))))

(deftest zip-end-fn-exists-as-a-convenience
  (let [p (get-in simple-test-part [:structs 1 :components 1])
        z (pz/part-zipper p)]
    (is (= (zip/end? z) (pz/end? z)))
    (is (= (loop [loc z] (if (zip/end? loc) loc (recur (zip/next loc))))
           (loop [loc z] (if (pz/end? loc) loc (recur (pz/next loc))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip lefts, rights, and children exist as a convenience

(deftest zip-lefts-rights-and-children-fns-exist-as-a-convenience
  (let [p (get-in simple-test-part [:structs 1 :components 1])
        z (pz/part-zipper p)]
    (is (= (zip/lefts z) (pz/lefts z)))
    (is (= (zip/rights z) (pz/rights z)))
    (is (= (zip/children z) (pz/children z)))
    (is (= (-> z zip/down zip/down zip/children)
           (-> z pz/down pz/down pz/children)))
    (is (= (-> z zip/down zip/down zip/down zip/rights)
           (-> z pz/down pz/down pz/down pz/rights)))
    (is (= (-> z zip/down zip/down zip/down zip/lefts)
           (-> z pz/down pz/down pz/down pz/lefts)))))
