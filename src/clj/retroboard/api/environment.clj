(ns retroboard.api.environment
  (:require [retroboard.environment :as env]
            [retroboard.util :refer [edn-resp]]
            [org.httpkit.server :refer [with-channel on-close on-receive send!]]
            [clojure.edn :as edn]
            [compojure.core :refer [routes GET POST]]))

(def subscriptions (atom {}))

(defn get-eid [channel]
  (ffirst (filter (fn [[eid channels]] (channels channel)) @subscriptions)))

(defn ping [channel]
  (send! channel (pr-str {:ping :pong})))

(defn ping-channel [channel]
  (future (while true
            (Thread/sleep 1000)
            (ping channel))))

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

(defn websocket-handler [request]
  (with-channel request channel
    (let [ping-future (ping-channel channel)]
      (on-close channel (fn [status]
                          (future-cancel ping-future)
                          (cmd-handler {:cmd :unregister} channel)))
      (on-receive channel (fn [data]
                            (cmd-handler (edn/read-string {:readers *data-readers*} data)
                                         channel))))))

(defn create-environment [req]
  (let [{:keys [initial-actions]} (:edn-params req)
        new-env-id (env/create)]
    (when initial-actions
      (env/append-actions new-env-id initial-actions))
    (edn-resp new-env-id 201)))

(def api-routes
  (routes
   (POST "/" [] create-environment)
   (GET "/" [] websocket-handler)))
