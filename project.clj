(defproject route-ccrs "1.0.1"
  :description "Calculates the current CCR for active routings in IFS"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.logging "0.3.1"]

                 [com.stuartsierra/component "0.2.2"]
                 [environ "1.0.0"]

                 [org.spootnik/logconfig "0.7.3"]

                 [org.clojure/java.jdbc "0.3.6"]
                 [org.clojars.zentrope/ojdbc "11.2.0.3.0"]
                 [hikari-cp "0.12.0"]
                 [yesql "0.5.0-rc1"]
                 [ragtime "0.3.8"]]

  :main route-ccrs.main

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.7"]]}
             :uberjar {:aot [route-ccrs.main]}
             :repl {:source-paths ["dev" "src"]}}

  :repl-options {:init-ns user
                 :init (user/init)})
