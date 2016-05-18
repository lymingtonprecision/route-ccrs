(ns user
  (:require [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.stuartsierra.component :as component]
            [environ.core :refer (env)]
            [clojure.java.jdbc :as jdbc]

            [route-ccrs.system :as sys]
            [route-ccrs.database :as db]))

(in-ns 'environ.core)

(defn refresh-env
  "A hack to allow in-repl refresh of the environment vars"
  []
  (def env
    (merge (read-env-file ".lein-env")
           (read-system-env)
           (read-system-props))))

(in-ns 'user)

(def system nil)

(defn init []
  (environ.core/refresh-env)
  (alter-var-root #'system (constantly (sys/system))))

(defn start []
  (alter-var-root #'system sys/start))

(defn stop []
  (if system
    (alter-var-root #'system sys/stop)))

(defn go
  "Initialize the current development system and start it's components"
  []
  (init)
  (start)
  :running)

(defn reset []
  (stop)
  (refresh :after 'user/go))
