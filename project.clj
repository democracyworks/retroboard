(defproject retroboard "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-cljsbuild "1.0.3"]
            [com.cemerick/clojurescript.test "0.3.0"]
            [com.keminglabs/cljx "0.4.0"]
            [lein-haml-sass "0.2.7-SNAPSHOT"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [om "0.6.4"]
                 [http-kit "2.1.16"]
                 [ring/ring-core "1.3.0"]
                 [compojure "1.1.6"]
                 [javax.servlet/servlet-api "2.5"]
                 [com.keminglabs/cljx "0.4.0"]
                 [com.taoensso/carmine "2.6.2"]]
  :hooks [cljx.hooks]
  :source-paths ["src/clj" "src/cljx"]
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
                       "scss" "once,"
                       "cljsbuild" "once" "production,"
                       "uberjar"]}
  :scss {:src "scss"
         :output-directory "resources/public/stylesheets"
         :output-extension "css"}
  :cljsbuild {
              :builds {:dev
                       {:source-paths ["src/cljs" "target/classes" "environments/dev"]
                        :compiler {:output-to "dev-resources/public/js/main.js"
                                   :output-dir "dev-resources/public/js/out"
                                   :optimizations :none
                                   :source-map true}}
                       :production
                       {:source-paths ["src/cljs" "target/classes" "environments/production"]
                        :compiler {:output-to "resources/public/js/prod.js"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :preamble ["react/react.min.js"]
                                   :externs ["react/externs/react.js"]}}}})
