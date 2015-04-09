(defproject route-ccrs "2.0.2"
  :description "Calculates the current CCR for active routings in IFS"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.logging "0.3.1"]

                 [com.stuartsierra/component "0.2.3"]
                 [environ "1.0.0"]

                 [org.spootnik/logconfig "0.7.3"]

                 [prismatic/schema "0.4.0"]

                 [clj-time "0.9.0"]

                 [org.clojure/java.jdbc "0.3.6"]
                 [org.clojars.zentrope/ojdbc "11.2.0.3.0"]
                 [hikari-cp "1.1.1"]
                 [yesql "0.5.0-rc2"]
                 [squirrel "0.1.1"]
                 [ragtime "0.3.8"]]

  :jvm-opts ["-Duser.timezone=UTC"]

  :main route-ccrs.main

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                                  [org.clojure/test.check "0.7.0"]
                                  [com.gfredericks/test.chuck "0.1.16"]]}
             :uberjar {:aot [route-ccrs.main]}
             :test {:source-paths ["dev" "src"]}
             :repl {:source-paths ["dev" "src"]}}

  :test-selectors {:default (complement :db)
                   :db :db
                   :all (constantly true)}

  :repl-options {:init-ns user
                 :init (user/init)})
