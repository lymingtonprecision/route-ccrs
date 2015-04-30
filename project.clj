(defproject lymingtonprecision/route-ccrs "3.1.0-SNAPSHOT"
  :description "A library for calculating part/routing end dates and CCRs"
  :url "https://github.com/lymingtonprecision/route-ccrs"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-beta2"]
                 [org.clojure/clojurescript "0.0-3211"]
                 [com.stuartsierra/component "0.2.3"]
                 [clj-time "0.9.0"]
                 [com.andrewmcveigh/cljs-time "0.3.4"]
                 [prismatic/schema "0.4.2"]
                 ; explicit instaparse for 1.7.x compatability
                 [instaparse "1.3.6"]
                 [yesql "0.5.0-rc2" :exclusions [instaparse]]
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
                                  [com.gfredericks/test.chuck "0.1.17"]]
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
