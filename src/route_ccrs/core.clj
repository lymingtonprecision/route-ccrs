(ns route-ccrs.core
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [route-ccrs.active-routes :as ar]
            [route-ccrs.route-ccr :as ccr]))

(defmacro with-db-connection
  ^{:private true}
  [sys & body]
  `(let [~'db (:db ~sys)
         ~'c {:connection ~'db}]
     ~@body))

(defn- route-update
  "Returns a record of any required database updates to reflect changes
  in the routes current CCR details, or `nil` if the CCR hasn't changed"
  [sys [route-id operations]]
  (with-db-connection sys
    (ccr/ccr-entry-updates
      route-id
      (ccr/get-last-known-ccr route-id c)
      (ccr/select-current-ccr db operations))))

(defn- route-processor
  "Returns a `go-loop` that takes route entries from the `<routes`
  channel, and puts details of any required database updates to the
  `>updates` channel.

  The `go-loop` will terminate when no more entries can be retrieved
  from the `>routes` channel."
  [sys <routes >updates]
  (async/go-loop
    []
    (let [r (async/<! <routes)]
      (if (nil? r)
        :done
        (let [u (route-update sys r)]
          (if (seq u) (async/>! >updates u))
          (recur))))))

(defn- reduce-routes-to-updates
  "Takes a collection of routes and returns a collection of required CCR
  database updates."
  [sys routes]
  (let [<routes (async/to-chan routes)
        >updates (async/chan 10)
        <updates (async/into [] >updates)
        !workers (async/merge
                   (map
                     (fn [n] (route-processor sys <routes >updates))
                     (range 10)))]
    (loop []
      (let [w (async/<!! !workers)]
        (if (nil? w)
          (do
            (async/close! >updates)
            (async/<!! <updates))
          (recur))))))

(defn- apply-updates! [sys updates]
  (with-db-connection sys
    (reduce
      (fn [totals route-update]
        (let [update-type (-> route-update second first)]
          (ccr/update! db route-update)
          (update-in totals [update-type] #(inc (or % 0)))))
      {}
      updates)))

(defn calculate-and-record-ccrs! [sys]
  (with-db-connection sys
    (log/info "CCR calculation started")
    (->> (into [] ar/transduce-routes (ar/active-routes {} c))
         (reduce-routes-to-updates sys)
         (apply-updates! sys)
         (log/info "calculation complete, updated:"))))
