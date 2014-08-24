(ns retroboard.server
  (:require [retroboard.api.environment :as api.env]
            [retroboard.api.user :as api.user]
            [retroboard.util :refer [wrap-edn-params]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.reload :as reload]
            [ring.util.response  :refer [resource-response]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [compojure.core :refer [defroutes context GET]]
            [compojure.handler :refer [site]])
  (:gen-class))


(defroutes app
  (GET "/" [] (resource-response "public/html/index.html"))
  (GET "/e/:env" [] (resource-response "public/html/index.html"))
  (context "/env" [] api.env/api-routes)
  (context "/user" [] api.user/api-routes))

(def web-handler
  (-> app
      site
      wrap-edn-params
      (wrap-resource "public")
      (wrap-file-info)))

(defn -main [& port]
  (let [port (Integer. (or (first port) "8080"))
        handler (if (= port 8080)
                  (reload/wrap-reload web-handler)
                  web-handler)]
    (run-server handler {:port port})
    (println "Running on " port)))
