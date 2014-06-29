(ns retroboard.server
  (:require [clojure.edn :as edn]
            [org.httpkit.server :refer :all]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [compojure.core :refer [defroutes GET]]
            [compojure.handler :refer [site]]))

(def environments (atom {}))

(defn new-resource-factory []
  (let [resource-id (atom 0)]
    (fn []
      (swap! resource-id inc))))

(defn get-env [channel]
  (first (filter (fn [e] (@(:clients e) channel)) (vals @environments))))

(defmulti cmd-handler (fn [data _] (:cmd data)))
(defmethod cmd-handler :register [data channel]
  (println "Registering " channel " to env " (:action data))
  (when-let [env (@environments (:action data))]
    (swap! (env :clients) conj channel)
    (send! channel (pr-str {:cmd :cmds :commands (-> env :history deref)}))))

(defmethod cmd-handler :unregister [data channel]
  (println "Unregistering " channel)
  (when-let [env (get-env channel)]
    (swap! (:clients env) disj channel)))

(defmethod cmd-handler :action [data channel]
  (println "Received " data)
  (when-let [env (get-env channel)]
    (swap! (:history env) conj (:action data))
    (dorun (map #(send! % (pr-str {:cmd :cmds :commands [(:action data)]}))
                (-> env :clients deref)))))

(defmethod cmd-handler :new-resource [data channel]
  (println "New resource")
  (when-let [env (get-env channel)]
    (let [rid ((:resource-factory env))]
      (send! channel (pr-str {:cmd :resource-id :resource-id rid})))))

(defn new-environment []
  {:clients (atom #{})
   :history (atom [])
   :resource-factory (new-resource-factory)})

(def env-resource-factory (new-resource-factory))

(defmethod cmd-handler :new-environment [data channel]
  (println "New environment")
  (let [rid (env-resource-factory)]
    (swap! environments assoc rid (new-environment))
    (send! channel (pr-str {:cmd :environment-id :environment-id rid}))))

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (cmd-handler {:cmd :unregister} channel)))
    (on-receive channel (fn [data]
                          (cmd-handler (edn/read-string data) channel)))))

(defroutes app
  (GET "/e" [] (resource-response "public/html/index.html"))
  (GET "/e/:env" [] (resource-response "public/html/index.html"))
  (GET "/ping" [] "pong!")
  (GET "/ws" [] handler))

(def web-handler
  (-> app
      site
      (wrap-resource "public")
      (wrap-file-info)))

(defn -main [& port]
  (let [port (Integer. (or (first port) "8080"))]
    (run-server web-handler {:port port})))
