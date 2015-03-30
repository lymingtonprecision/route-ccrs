(ns route-ccrs.part-zipper-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [schema.test]
            [clj-time.core :as t]
            [route-ccrs.schema.purchased-raw-part-test :refer [gen-raw-part]]
            [clojure.zip :as zip :refer [up down left right node]]
            [route-ccrs.part-zipper :refer [part-zipper root-part]]))

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

(deftest top-level-traversal
  (let [p simple-test-part
        z (part-zipper p)]
    (is (nil? (zip/left z)))
    (is (nil? (zip/right z)))
    (is (= #{:components :routes}
           (->> z zip/down zip/children (map #(-> % keys first)) set)))))

(deftest root-part-returns-part-at-root-of-zipper
  (let [p simple-test-part
        z (part-zipper p)]
    (is (= p (-> z down down down down down root-part)))))

(deftest struct-nodes-are-single-element-maps-of-the-struct-entry
  (let [p simple-test-part
        z (part-zipper p)]
    (is (= (-> z down node) {1 (-> p :structs (get 1))}))))

(deftest component-nodes-are-single-element-maps-of-the-component-entry
  (let [rp (first (gen/sample (gen-raw-part) 1))
        p (assoc-in simple-test-part [:structs 1 :components 2] rp)
        z (part-zipper p)]
    (is (= (set (reduce
                 (fn [r [k v]] (conj r {k v}))
                 []
                 (get-in p [:structs 1 :components])))
           (-> z down down zip/children set)))))

(deftest route-nodes-are-single-element-maps-of-the-route-entry
  (let [p simple-test-part
        z (part-zipper p)]
    (is (= (set (reduce
                 (fn [r [k v]] (conj r {k v}))
                 []
                 (get-in p [:structs 1 :components 1 :structs 1 :routes])))
           (-> z down down down down down right zip/children set)))))

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
        z (part-zipper p)
        zs (zip/children z)]
    (is (= (count zs) (count (set zs))))
    (is (= (set zs)
           (set (->> p :structs (reduce (fn [r [k v]] (conj r {k v})) [])))))))

(deftest struct-traversal
  (let [p simple-test-part
        z (part-zipper p)]
    (is (= (-> z down node) {1 (get-in p [:structs 1])}))
    (is (= (-> z down down down node) {1 (get-in p [:structs 1 :components 1])}))
    (is (= (-> z down down down down down right down node)
           {1 (get-in p [:structs 1
                         :components 1
                         :structs 1
                         :routes 1])}))))

(deftest structs-without-components-still-branch-to-that-level
  (let [childless-part (assoc-in simple-test-part [:structs 1 :components] {})
        z (part-zipper childless-part)]
    (is (zip/branch? (-> z down down)))))

(deftest routes-have-no-children
  (let [p (get-in simple-test-part [:structs 1 :components 1])
        r {1 (get-in p [:structs 1 :routes 1])}
        z (part-zipper p)
        rn (-> z down down right down)]
    (is (= (node rn) r))
    (is (not (zip/branch? rn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/edit

(deftest editing-structure-and-component-end-dates
  (let [d (java.util.Date.)
        p simple-test-part
        z (part-zipper p)
        update-best-end-date (fn [n d]
                               (assoc-in n
                                         (conj (vec (keys n)) :best-end-date)
                                         d))
        ep (-> z down down down
               (zip/edit update-best-end-date d)
               up
               up
               (zip/edit update-best-end-date d)
               root-part)]
    (is (= ep
           (-> p
               (assoc-in [:structs 1 :components 1 :best-end-date] d)
               (assoc-in [:structs 1 :best-end-date] d))))))

(deftest editing-a-routing
  (let [d (java.util.Date.)
        p simple-test-part
        e {:best-end-date d
           :ccr MC032
           :total-touch-time 10
           :total-buffer 5}
        z (part-zipper p)
        ep (-> z down down down down down right down
               (zip/edit (fn [n m] (update-in n (keys n) merge m)) e)
               root-part)]
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
           (loop [z (part-zipper p) incs i]
             (if (zip/end? z)
               (root-part z)
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
  (let [d (java.util.Date.)
        ep simple-test-part
        r (get-in ep [:structs 1 :components 1 :structs 1 :routes 1])
        c {:best-end-date d
           :ccr {:id (:id MC032)
                 :operation 10
                 :total-touch-time 5
                 :pre-ccr-buffer 1
                 :post-ccr-buffer 5}
           :total-touch-time 10
           :total-buffer 5}
        p (update-in ep [:structs 1 :components 1 :structs 1 :routes 1]
                     merge c)
        z (part-zipper p)]
    (is (= ep
           (-> z down down down down down right down
               (zip/replace {1 r})
               root-part)))))

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
        z (part-zipper p)
        k (rand-nth (-> p :structs keys))]
    (is (= (update-in p [:structs] dissoc k)
           (->> z down
                (#(if (= (keys (zip/node %)) [k]) % (recur (zip/right %))))
                zip/remove
                root-part)))))

(deftest removing-a-component
  (let [p simple-test-part
        z (part-zipper p)
        k 2]
    (is (= (update-in p [:structs 1 :components 1 :structs 1 :components]
                      dissoc
                      k)
           (->> z down down down down down down
                (#(if (= (keys (zip/node %)) [k]) % (zip/right %)))
                zip/remove
                root-part)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/append-child

(deftest appending-a-structure
  (let [p simple-test-part
        k 2
        v {:id {:type :purchased :revision 1 :alternative "2"}
           :lead-time 10 :best-end-date nil :components {}}
        z (part-zipper p)]
    (is (= (update-in p [:structs] assoc k v)
           (-> z (zip/append-child {k v}) root-part)))))

(deftest appending-a-component
  (let [c (first (gen/sample (gen-raw-part)))
        p simple-test-part
        z (part-zipper p)
        ep (-> z down down
               (zip/append-child {2 c})
               root-part)]
    (is (= (assoc-in p [:structs 1 :components 2] c) ep))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/insert-child

(deftest inserting-a-child-structure
  (let [p simple-test-part
        k 2
        v {:id {:type :purchased :revision 1 :alternative "2"}
           :lead-time 10 :best-end-date nil :components {}}
        z (part-zipper p)]
    (is (= (update-in p [:structs] assoc k v)
           (-> z (zip/insert-child {k v}) root-part)))))

(deftest inserting-a-child-component
  (let [c (first (gen/sample (gen-raw-part)))
        p simple-test-part
        z (part-zipper p)
        ep (-> z down down
               (zip/insert-child {2 c})
               root-part)]
    (is (= (assoc-in p [:structs 1 :components 2] c) ep))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zip/insert-left (and, effectively, zip/insert-right)

(deftest inserting-a-sibling-structure
  (let [p simple-test-part
        k 2
        v {:id {:type :purchased :revision 1 :alternative "2"}
           :lead-time 10 :best-end-date nil :components {}}
        z (part-zipper p)]
    (is (= (update-in p [:structs] assoc k v)
           (-> z down (zip/insert-left {k v}) root-part)))))

(deftest inserting-a-sibling-component
  (let [c (first (gen/sample (gen-raw-part)))
        p simple-test-part
        z (part-zipper p)
        ep (-> z down down down
               (zip/insert-left {2 c})
               root-part)]
    (is (= (assoc-in p [:structs 1 :components 2] c) ep))))
