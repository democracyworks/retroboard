(defproject retroboard "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-cljsbuild "1.0.3"]
            [com.cemerick/clojurescript.test "0.3.0"]
            [com.keminglabs/cljx "0.4.0"]
            [deraen/lein-sass4clj "0.3.1"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [org.clojure/core.async "0.1.319.0-6b1aca-alpha"]
                 [om "0.6.4"]
                 [http-kit "2.1.16"]
                 [ring/ring-core "1.3.0"]
                 [ring/ring-devel "1.3.0"]
                 [compojure "1.1.6"]
                 [javax.servlet/servlet-api "2.5"]
                 [com.keminglabs/cljx "0.4.0"]
                 [com.taoensso/carmine "2.6.2"]
                 [com.cemerick/friend "0.2.1" :exclusions [org.clojure/core.cache]]
                 [com.novemberain/monger "2.0.0"]]
  :source-paths ["src/clj" "src/cljx"]
  :auto-clean false
  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/classes"
                   :rules :clj}
                  {:source-paths ["src/cljx"]
                   :output-path "target/classes"
                   :rules :cljs}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :clj}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :cljs}]}
  :uberjar-name "retroboard-standalone.jar"
  :min-lein-version "2.0.0"
  :main retroboard.server
  :aliases {"uberjar" ["do" "cljx" "once,"
                       "sass4clj" "once,"
                       "cljsbuild" "once" "production,"
                       "uberjar"]}
  :sass {:source-paths ["scss"]
         :target-path "resources/public/stylesheets"}
  :cljsbuild {
              :builds {:dev
                       {:source-paths ["src/cljs" "target/classes"]
                        :compiler {:output-to "dev-resources/public/js/main.js"
                                   :output-dir "dev-resources/public/js/out"
                                   :optimizations :none
                                   :source-map true}}
                       :production
                       {:source-paths ["src/cljs" "target/classes" "vendor"]
                        :compiler {:output-to "resources/public/js/prod.js"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :preamble ["ZeroClipboard.min.js"
                                              "react/react.min.js"]
                                   :externs ["react/externs/react.js"
                                             "externs.js"]}}}})
