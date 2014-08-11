(ns retroboard.server
  (:require [retroboard.user :as user]
            [retroboard.api.environment :as api.env]
            [retroboard.util :refer [wrap-edn-params edn-resp]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.reload :as reload]
            [ring.util.response :as resp :refer [resource-response]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [compojure.core :refer [defroutes context GET ANY POST]]
            [compojure.handler :refer [site]]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]))

(defn boards [req]
  (friend/authorize #{::user/user}
                    (edn-resp (user/boards (:current (friend/identity req))))))

(defn add-board [req]
  (let [{:keys [eid]} (:params req)]
    (friend/authorize #{::user/user}
                      (user/add-board (:current (friend/identity req)) eid)
                      (edn-resp "OK"))))

(defn signup [req]
  (let [{:keys [username email password]} (:params req)
        user (user/add-user username email password)]
    (if user
      (friend/merge-authentication (edn-resp "Created!" 201) (assoc user :identity username))
      (edn-resp "Problem" 500))))

(defroutes app
  (GET "/" [] (resource-response "public/html/index.html"))
  (GET "/e/:env" [] (resource-response "public/html/index.html"))
  (GET "/boards" [] boards)
  (POST "/boards/add" [] add-board)
  (POST "/signup" [] signup)
  (context "/env" [] api.env/api-routes)
  (friend/logout (ANY "/logout" request (resp/redirect "/"))))

(def web-handler
  (-> app
      (friend/authenticate
       {:login-uri "/login"
        :default-landing-uri "/"
        :unauthenticated-handler (fn [_] (edn-resp "You need to login to see that." 401))
        :credential-fn user/cred-fn
        :workflows [(workflows/interactive-form
                     :login-failure-handler (fn [_] (edn-resp "Invalid email or password" 401))
                     :redirect-on-auth? false)]})
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
    (api.env/ping-clients)
    (println "Running on " port)))
