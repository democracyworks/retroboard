(ns retroboard.server
  (:require [retroboard.environment :as env]
            [clojure.edn :as edn]
            [org.httpkit.server :refer :all]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [compojure.core :refer [defroutes GET]]
            [compojure.handler :refer [site]]))


(def subscriptions (atom {}))

(defn get-eid [channel]
  (ffirst (filter (fn [[eid channels]] (channels channel)) @subscriptions)))


(defmulti cmd-handler (fn [data _] (:cmd data)))
(defmethod cmd-handler :register [data channel]
  (println "Registering " channel " to env " (:action data))
  (let [eid (:action data)]
    (if (env/exists? eid)
      (do
        (swap! subscriptions #(update-in % [eid] (comp set conj) channel))
        (send! channel (pr-str {:cmd :cmds :commands (env/history eid)})))
      (send! channel (pr-str {:cmd :error :error :no-such-environment})))))

(defmethod cmd-handler :unregister [data channel]
  (println "Unregistering " channel)
  (when-let [eid (get-eid channel)]
    (swap! subscriptions #(update-in % [eid] disj channel))))

(defmethod cmd-handler :actions [data channel]
  (println "Received " data)
  (when-let [eid (get-eid channel)]
    (let [actions (env/append-actions eid (:actions data))]
      (dorun (map #(send! % (pr-str {:cmd :cmds :commands actions}))
                  (@subscriptions eid))))))

(defmethod cmd-handler :action [data channel]
  (cmd-handler {:cmd :actions :actions [(:action data)]} channel))

(defn all-clients []
  (apply clojure.set/union (vals @subscriptions)))

(defn ping [channel]
  (send! channel (pr-str {:ping :pong})))

(defn ping-clients []
  (future (loop []
            (Thread/sleep 1000)
            (dorun (map ping (all-clients)))
            (recur))))

(defmethod cmd-handler :new-environment [data channel]
  (println "New environment")
  (let [new-env-id (env/create)]
    (when-let [actions (:initial-actions data)]
      (env/append-actions new-env-id actions))
    (send! channel (pr-str {:cmd :environment-id :environment-id new-env-id}))))

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
