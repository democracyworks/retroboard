(defproject retroboard "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-cljsbuild "1.0.3"]
            [com.cemerick/clojurescript.test "0.3.0"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [om "0.6.4"]
                 [http-kit "2.1.16"]
                 [ring/ring-core "1.3.0"]
                 [compojure "1.1.6"]
                 [javax.servlet/servlet-api "2.5"]]
  :uberjar-name "retroboard-standalone.jar"
  :min-lein-version "2.0.0"
  :main retroboard.server
  :cljsbuild {
              :builds {:dev
                       {:source-paths ["src"]
                        :compiler {:output-to "dev-resources/public/js/main.js"
                                   :output-dir "dev-resources/public/js/out"
                                   :optimizations :none
                                   :source-map true}}
                       :production
                       {:source-paths ["src"]
                        :compiler {:output-to "resources/public/js/prod.js"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :preamble ["react/react.min.js"]
                                   :externs ["react/externs/react.js"]}}}})
