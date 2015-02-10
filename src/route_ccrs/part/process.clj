(ns route-ccrs.part.process
  (:require [clojure.core.async :as async]
            [route-ccrs.part :refer :all]
            [route-ccrs.route-ccr :as ccr]))

(defn- promise-transduced-query
  [db query-fn xf coll]
  (let [>rows (async/chan 10 xf)
        <result (async/into coll >rows)
        p (promise)]
    (async/go
      (query-fn {:connection db :row-fn #(async/>!! >rows %)})
      (async/close! >rows)
      (let [r (async/<! <result)]
        (deliver p r)))
    p))

(defn- parallel-worker
  ([process-fn <queue >results]
   (parallel-worker process-fn <queue >results false))
  ([process-fn <queue >results batched-results?]
   (async/go-loop
     []
     (let [i (async/<! <queue)]
       (if (nil? i)
         :done
         (do
           (if-let [r (process-fn i)]
             (if batched-results?
               (async/<! (async/onto-chan >results r false))
               (async/>! >results r)))
           (recur)))))))

(defn- parallel-process
  ([process-fn coll] (parallel-process process-fn coll 10 false))
  ([process-fn coll workers batched-results?]
   (let [<items (async/to-chan coll)
         >results (async/chan 10)
         <results (async/into [] >results)
         !workers (async/merge
                    (map
                      (fn [_]
                        (parallel-worker
                          process-fn
                          <items
                          >results
                          batched-results?))
                      (range workers)))]
     (loop []
       (let [w (async/<!! !workers)]
         (if (nil? w)
           (do
             (async/close! >results)
             (async/<!! <results))
           (recur)))))))

(defn deferred-routes [db part]
  (promise-transduced-query
    db
    (partial operations-for-part part)
    transduce-routes
    {}))

(defn process-route [db [route-id operations]]
  (let [c {:connection db}]
    (ccr/ccr-entry-updates
      route-id
      (ccr/get-last-known-ccr route-id c)
      (ccr/select-current-ccr operations c))))

(defn process-routes [db routes]
  (parallel-process (partial process-route db) routes))

(defn deferred-structs [db part]
  (promise-transduced-query
    db
    (partial components-for-part part)
    transduce-structures
    {}))

(defn process-part [db part]
  (let [routes (deferred-routes db part)]
    (process-routes db @routes)))

(defn process-parts [db parts]
  (parallel-process (partial process-part db) parts 10 true))
