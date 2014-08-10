(ns retroboard.server
  (:require [retroboard.environment :as env]
            [retroboard.user :as user]
            [clojure.edn :as edn]
            [org.httpkit.server :refer :all]
            [ring.middleware.reload :as reload]
            [ring.util.response :as resp :refer [resource-response]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [compojure.core :refer [defroutes GET ANY POST]]
            [compojure.handler :refer [site]]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]))


(def subscriptions (atom {}))

(defn get-eid [channel]
  (ffirst (filter (fn [[eid channels]] (channels channel)) @subscriptions)))


(defmulti cmd-handler (fn [data _ _] (:cmd data)))
(defmethod cmd-handler :register [data channel identity]
  (println "Registering " channel " to env " (:action data))
  (let [eid (:action data)]
    (if (env/exists? eid)
      (do
        (if identity (user/add-board (:current identity) eid))
        (swap! subscriptions #(update-in % [eid] (comp set conj) channel))
        (send! channel (pr-str {:cmd :cmds :commands (env/history eid)})))
      (send! channel (pr-str {:cmd :error :error :no-such-environment})))))

(defmethod cmd-handler :unregister [data channel identity]
  (println "Unregistering " channel)
  (when-let [eid (get-eid channel)]
    (swap! subscriptions #(update-in % [eid] disj channel))))

(defmethod cmd-handler :actions [data channel identity]
  (println "Received " data)
  (when-let [eid (get-eid channel)]
    (let [actions (env/append-actions eid (:actions data))]
      (dorun (map #(send! % (pr-str {:cmd :cmds :commands actions}))
                  (@subscriptions eid))))))

(defmethod cmd-handler :action [data channel identity]
  (cmd-handler {:cmd :actions :actions [(:action data)]} channel identity))

(defn all-clients []
  (apply clojure.set/union (vals @subscriptions)))

(defn ping [channel]
  (send! channel (pr-str {:ping :pong})))

(defn ping-clients []
  (future (loop []
            (Thread/sleep 1000)
            (dorun (map ping (all-clients)))
            (recur))))

(defmethod cmd-handler :new-environment [data channel identity]
  (println "New environment: " data)
  (let [new-env-id (env/create)]
    (when-let [actions (:initial-actions data)]
      (env/append-actions new-env-id actions))
    (send! channel (pr-str {:cmd :environment-id :environment-id new-env-id}))))

(defn handler [request]
  (with-channel request channel
    (let [identity (friend/identity request)]
      (on-close channel (fn [status] (cmd-handler {:cmd :unregister} channel identity)))
      (on-receive channel (fn [data]
                            (cmd-handler (edn/read-string {:readers *data-readers*} data)
                                         channel
                                         identity))))))

(defn edn-resp [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn boards [req]
  (friend/authorize #{::user/user}
                    (edn-resp (user/boards (:current (friend/identity req))))))

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
  (POST "/signup" [] signup)
  (GET "/ws" [] handler)
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
    (ping-clients)
    (println "Running on " port)))
