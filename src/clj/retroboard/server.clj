(ns retroboard.server
  (:require [retroboard.resource :as resource]
            [retroboard.util :refer [unique-str-generator]]
            [clojure.edn :as edn]
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
  (if-let [env (@environments (:action data))]
    (do
      (swap! (env :clients) conj channel)
      (send! channel (pr-str {:cmd :cmds :commands (-> env :history deref)})))
    (send! channel (pr-str {:cmd :error :error :no-such-environment}))))

(defmethod cmd-handler :unregister [data channel]
  (println "Unregistering " channel)
  (when-let [env (get-env channel)]
    (swap! (:clients env) disj channel)))

(defmethod cmd-handler :actions [data channel]
  (println "Received " data)
  (when-let [env (get-env channel)]
    (let [actions (resource/replace-ids (:resource-factory env) (:actions data))]
      (swap! (:history env) concat actions)
      (dorun (map #(send! % (pr-str {:cmd :cmds :commands actions}))
                  (-> env :clients deref))))))

(defmethod cmd-handler :action [data channel]
  (cmd-handler {:cmd :actions :actions [(:action data)]} channel))

(defmethod cmd-handler :new-resource [data channel]
  (println "New resource")
  (when-let [env (get-env channel)]
    (let [rid ((:resource-factory env))]
      (send! channel (pr-str {:cmd :resource-id :resource-id rid})))))

(defn new-environment []
  {:clients (atom #{})
   :history (atom [])
   :resource-factory (new-resource-factory)})

(defn all-clients []
  (mapcat (fn [env] @(:clients env)) (vals @environments)))

(defn ping [channel]
  (send! channel (pr-str {:ping :pong})))

(defn ping-clients []
  (future (loop []
            (Thread/sleep 1000)
            (dorun (map ping (all-clients)))
            (recur))))

(def env-resource-factory (unique-str-generator 5))

(defmethod cmd-handler :new-environment [data channel]
  (println "New environment")
  (let [rid (env-resource-factory)
        new-env (new-environment)]
    (swap! environments assoc rid new-env)
    (when-let [actions (:initial-actions data)]
      (swap! (:history new-env) concat
             (resource/replace-ids (:resource-factory new-env)
                                   actions)))
    (send! channel (pr-str {:cmd :environment-id :environment-id rid}))))

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (cmd-handler {:cmd :unregister} channel)))
    (on-receive channel (fn [data]
                          (cmd-handler (edn/read-string {:readers *data-readers*} data) channel)))))

(defroutes app
  (GET "/" [] (resource-response "public/html/index.html"))
  (GET "/e/:env" [] (resource-response "public/html/index.html"))
  (GET "/ws" [] handler))

(def web-handler
  (-> app
      site
      (wrap-resource "public")
      (wrap-file-info)))

(defn -main [& port]
  (let [port (Integer. (or (first port) "8080"))]
    (run-server web-handler {:port port})
    (ping-clients)))
