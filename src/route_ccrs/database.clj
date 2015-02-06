(ns route-ccrs.database
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [hikari-cp.core :as hk]
            [ragtime.core :as rt]
            [ragtime.sql.database :as rt-db]
            [ragtime.sql.files :as rt-files]))

(def default-options
  (merge
    hk/default-datasource-options
    {:auto-commit true
     :read-only false
     :minimum-idle 2
     :maximum-pool-size 20
     :adapter "oracle"
     :driver-type "thin"
     :port-number 1521}))

(def env-keys-to-option-names
  {:db-name :database-name
   :db-server :server-name
   :db-user :username})

(defn is-env-key? [k] (re-find #"(?i)^:?db-" (str k)))

(defn strip-env-key [k]
  (-> (str k)
      (clojure.string/replace #"(?i)^:?db-" "")
      keyword))

(defn extract-options-from-env [env]
  (->> env
       (filter #(is-env-key? (first %)))
       (reduce
         (fn [opts [k v]]
           (let [o (or (k env-keys-to-option-names)
                       (strip-env-key k))]
             (assoc opts o v)))
         {})))

(defn merge-options-with-env [env]
  (merge default-options (extract-options-from-env env)))

(defn migrations []
  (rt-files/migrations))

(defrecord Database [env]
  component/Lifecycle
  (start [this]
    (let [o (merge-options-with-env env)
          ds (hk/make-datasource o)]
      (rt/migrate-all (rt-db/map->SqlDatabase {:datasource ds}) (migrations))
      (assoc this
             :options o
             :datasource ds)))

  (stop [this]
    (if-let [ds (:datasource this)]
      (hk/close-datasource ds))
    (dissoc this :datasource)))

(defn database []
  (component/using
    (map->Database {})
    [:env]))
