(defproject lymingtonprecision/route-ccrs "3.4.1-SNAPSHOT"
  :description "A library for calculating part/routing end dates and CCRs"
  :url "https://github.com/lymingtonprecision/route-ccrs"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.stuartsierra/component "0.3.1"]
                 [clj-time "0.11.0"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [prismatic/schema "1.1.1"]
                 [yesql "0.5.3"]
                 [squirrel "0.1.2"]]

  :jvm-opts ["-Duser.timezone=UTC"]

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/clojurescript "1.8.51"]

                                  [environ "1.0.3"]

                                  [org.clojure/tools.logging "0.3.1"]
                                  [org.spootnik/logconfig "0.7.3"]

                                  [org.clojure/java.jdbc "0.6.1"]
                                  [org.clojars.zentrope/ojdbc "11.2.0.3.0"]
                                  [hikari-cp "1.6.1"]

                                  [org.clojure/tools.namespace "0.2.10"]

                                  [org.clojure/test.check "0.9.0"]
                                  [com.gfredericks/test.chuck "0.2.6"]]
                   :plugins [[lein-cljsbuild "1.0.5"]]}
             :test {:source-paths ["dev" "src"]}
             :repl {:source-paths ["dev" "src"]}}

  :cljsbuild {:builds
              {:test
               {:source-paths ["src" "test"]
                :compiler
                {:optimizations :whitespace
                 :pretty-print true
                 :output-dir "target/js/test"
                 :output-to "target/route-ccrs-test.js"
                 :source-map "target/route-ccrs-test.js.map"}}}
              :test-commands {"cljs" ["phantomjs"
                                      "phantom/unit-test.js"
                                      "phantom/unit-test.html"]}}

  :test-selectors {:default (fn [m] (not (some #(get m %) [:db :integration])))
                   :db :db
                   :integration :integration
                   :all (constantly true)}

  :repl-options {:init-ns user
                 :init (user/init)})
