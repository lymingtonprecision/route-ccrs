(ns route-ccrs.processing
  (:require [clojure.core.async :as async]))

(defn transduce-by-shared-id
  [id-fn set-fn count-key step]
  (let [sets (volatile! {})]
    (fn
      ([] (step))
      ([r]
       (let [v (if (empty? @sets) r (reduce step r @sets))]
         (vreset! sets {})
         (step v)))
      ([r e]
       (let [i (id-fn e)
             s (conj (get @sets i (set-fn)) (dissoc e count-key))]
         (if (= (int (count s)) (int (get e count-key)))
           (do
             (vreset! sets (dissoc @sets i))
             (step r [i s]))
           (do
             (vreset! sets (assoc @sets i s))
             r)))))))

(defn promise-transduced-query
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

(defn parallel-worker
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

(defn parallel-process
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
