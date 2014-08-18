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

(def env-chans (atom {}))
(def env-mults (atom {}))

(defn chan-for [eid]
  (or (@env-chans eid)
      (let [c (chan)]
        (swap! env-chans assoc eid c)
        (go (while true
              (let [actions (<! c)]
                (env/publish-actions eid actions))))
        c)))

(defn mult-for [eid]
  (or (@env-mults eid)
      (let [m (mult (map< (fn [a] {:cmd :cmds :commands a}) (env/subscribe eid)))]
        (swap! env-mults assoc eid m)
        m)))

(defmulti cmd-handler (fn [data _ _] (:cmd data)))
(defmethod cmd-handler :register [data eid-atom out-ch]
  (println "Registering to env " (:action data))
  (let [eid (:action data)]
    (if (env/exists? eid)
      (do
        (put! out-ch {:cmd :cmds :commands (env/history eid)})
        (tap (mult-for eid) out-ch)
        (reset! eid-atom eid))
      (put! out-ch {:cmd :error :error :no-such-environment}))))

(defmethod cmd-handler :unregister [data eid-atom out-ch]
  (println "Unregistering")
  (when (and @eid-atom (env/exists? @eid-atom))
    (untap (mult-for @eid-atom) out-ch)
    (reset! eid-atom nil)))

(defmethod cmd-handler :actions [data eid-atom out-ch]
  (println "Received " data " for " @eid-atom)
  (when (and @eid-atom (env/exists? @eid-atom))
    (let [actions (env/append-actions @eid-atom (:actions data))]
      (put! (chan-for @eid-atom) actions))))

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
  (let [{:keys [initial-actions]} (:edn-params req)
        new-env-id (env/create)]
    (when initial-actions
      (env/append-actions new-env-id initial-actions))
    (edn-resp new-env-id 201)))

(def api-routes
  (routes
   (POST "/" [] create-environment)
   (GET "/" [] websocket-handler)))
