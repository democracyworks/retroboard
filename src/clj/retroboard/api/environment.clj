(ns retroboard.api.environment
  (:require [retroboard.environment :as env]
            [retroboard.util :refer [edn-resp]]
            [clojure.core.async :refer [chan mult put! tap untap <! map< go-loop go]]
            [org.httpkit.server :refer [with-channel on-close on-receive send!]]
            [clojure.edn :as edn]
            [compojure.core :refer [routes GET POST]]))

(defn ping [channel]
  (send! channel (pr-str {:ping :pong})))

(defn ping-channel [channel]
  (future (while true
            (Thread/sleep 1000)
            (ping channel))))

(def env-mults (atom [{}]))

(defn mult-for [eid]
  (let [ch (chan 3)
        [new-env-mults subscribed?]
        (swap! env-mults (fn [[env-mults]]
                           (if (env-mults eid) [env-mults true]
                               [(assoc env-mults eid
                                       (mult (map< (fn [a] {:cmd :cmds :commands a}) ch)))
                                false])))]
    (when-not subscribed?
      (env/subscribe eid ch))
    (new-env-mults eid)))

(defmulti cmd-handler (fn [data _ _] (:cmd data)))
(defmethod cmd-handler :register [data eid-atom out-ch]
  (println "Registering to env " (:action data))
  (let [eid (:action data)]
    (if (env/exists? eid)
      (do
        (put! out-ch {:cmd :cmds :commands (env/history eid)})
        (tap (mult-for eid) out-ch)
        (env/on-join eid)
        (reset! eid-atom eid))
      (put! out-ch {:cmd :error :error :no-such-environment}))))

(defmethod cmd-handler :unregister [data eid-atom out-ch]
  (println "Unregistering")
  (when (and @eid-atom (env/exists? @eid-atom))
    (untap (mult-for @eid-atom) out-ch)
    (env/on-leave @eid-atom)
    (reset! eid-atom nil)))

(defmethod cmd-handler :actions [data eid-atom out-ch]
  (println "Received " data " for " @eid-atom)
  (when (and @eid-atom (env/exists? @eid-atom))
    (env/append-actions @eid-atom (:actions data))))

(defmethod cmd-handler :action [data eid-atom out-ch]
  (cmd-handler {:cmd :actions :actions [(:action data)]} eid-atom out-ch))

(defn websocket-handler [request]
  (with-channel request channel
    (let [ping-future (ping-channel channel)
          out-chan (chan 5)
          eid (atom nil)]
      (go-loop []
               (when-let [msg (<! out-chan)]
                 (send! channel (pr-str msg))
                 (recur)))
      (on-close channel (fn [status]
                          (future-cancel ping-future)
                          (cmd-handler {:cmd :unregister} eid out-chan)))
      (on-receive channel (fn [data]
                            (cmd-handler (edn/read-string {:readers *data-readers*} data)
                                         eid out-chan))))))

(defn create-environment [req]
  (let [{:keys [initial-actions on-join on-leave]} (:edn-params req)
        new-env-id (env/create on-join on-leave)]
    (when initial-actions
      (env/append-actions new-env-id initial-actions))
    (edn-resp new-env-id 201)))

(def api-routes
  (routes
   (POST "/" [] create-environment)
   (GET "/" [] websocket-handler)))
