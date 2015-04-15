(defproject route-ccrs "2.0.2"
  :description "Calculates the current CCR for active routings in IFS"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [clj-time "0.9.0"]
                 [prismatic/schema "0.4.0"]
                 [yesql "0.5.0-rc2"]
                 [squirrel "0.1.1"]]

  :jvm-opts ["-Duser.timezone=UTC"]

  :profiles {:dev {:dependencies [[environ "1.0.0"]

                                  [org.clojure/tools.logging "0.3.1"]
                                  [org.spootnik/logconfig "0.7.3"]

                                  [org.clojure/java.jdbc "0.3.6"]
                                  [org.clojars.zentrope/ojdbc "11.2.0.3.0"]
                                  [hikari-cp "1.2.2"]

                                  [org.clojure/tools.namespace "0.2.10"]

                                  [org.clojure/test.check "0.7.0"]
                                  [com.gfredericks/test.chuck "0.1.16"]]}
             :test {:source-paths ["dev" "src"]}
             :repl {:source-paths ["dev" "src"]}}

  :test-selectors {:default (fn [m] (not (some #(get m %) [:db :integration])))
                   :db :db
                   :integration :integration
                   :all (constantly true)}

  :repl-options {:init-ns user
                 :init (user/init)})
