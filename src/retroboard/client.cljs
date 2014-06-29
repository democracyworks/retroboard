(ns retroboard.client
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [retroboard.config :as config]
            [cljs.core.async :refer [chan <! put! pub sub unsub]]
            [goog.events :as events]
            [cljs.reader :as reader]
            [clojure.string :refer [split]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import goog.net.WebSocket
           goog.net.WebSocket.EventType))

(enable-console-print!)


(defn web-socket []
  (let [ws (WebSocket.)
        to-send (chan 5)
        incoming (chan 5)]
    (events/listen ws EventType.OPENED
                   (fn [e]
                     (go-loop []
                              (let [msg (pr-str (<! to-send))]
                                (.send ws msg))
                              (recur))))
    (events/listen ws EventType.MESSAGE
                   (fn [e]
                     (let [msg (reader/read-string (.-message e))]
                       (put! incoming msg))))
    (.open ws config/ws-url)
    {:to-send to-send :incoming (pub incoming :cmd) :websocket ws}))

(defn new-resource [{:keys [to-send incoming]}]
  (let [resource-chan (chan)
        return-chan (chan)]
    (sub incoming :resource-id resource-chan)
    (go
     (let [resource-id (:resource-id (<! resource-chan))]
       (unsub incoming :resource-id resource-chan)
       (>! return-chan resource-id)))
    (put! to-send {:cmd :new-resource})
    return-chan))

(defn new-environment [{:keys [to-send incoming]}]
  (let [env-chan (chan)
        return-chan (chan)]
    (sub incoming :environment-id env-chan)
    (go
     (let [env-id (:environment-id (<! env-chan))]
       (unsub incoming :environment-id env-chan)
       (>! return-chan env-id)))
    (put! to-send {:cmd :new-environment})
    return-chan))

(defn new-column [connection header]
  (let [resource-chan (new-resource connection)]
    (go
     (let [rid (<! resource-chan)]
       (>! (:to-send connection) {:cmd :action :action [:new-column {:id rid :header header}]})))))

(defn new-note [connection column text]
  (let [resource-chan (new-resource connection)]
    (go
     (let [rid (<! resource-chan)]
       (>! (:to-send connection) {:cmd :action :action [:new-note {:id rid :column column :text text}]})))))

(defn delete-note [connection column note]
  (go (>! (:to-send connection) {:cmd :action :action [:delete-note {:column column :id note}]})))

(defn new-vote [connection column note]
  (let [resource-chan (new-resource connection)]
    (go
     (let [rid (<! resource-chan)]
       (>! (:to-send connection) {:cmd :action :action [:new-vote {:id rid
                                                                   :column column
                                                                   :note note}]})))))

(defmulti apply-action (fn [_ action] (first action)))

(defmethod apply-action :new-column [initial-state [_ action]]
  (let [{:keys [id header]} action]
    (assoc initial-state id {:header header :notes {}})))

(defmethod apply-action :new-note [initial-state [_ action]]
  (let [{:keys [id column text]} action]
    (assoc-in initial-state [column :notes id] {:text text :votes #{}})))

(defmethod apply-action :delete-note [initial-state [_ action]]
  (let [{:keys [column id]} action]
    (update-in initial-state [column :notes] dissoc id)))

(defmethod apply-action :new-vote [initial-state [_ action]]
  (let [{:keys [id column note]} action]
    (update-in initial-state [column :notes note :votes] conj id)))

(defn apply-actions [actions initial-state]
  (reduce apply-action initial-state actions))




(defn create-column-button [connection owner]
  (reify
    om/IInitState
    (init-state [_] {:header ""})
    om/IRenderState
    (render-state [this {:keys [header]}]
      (letfn [(create-column []
                (when (seq header)
                  (new-column (om/value connection) header)
                  (om/set-state! owner :header "")))]
        (dom/div nil
                 (dom/label #js {:htmlFor "new-column"} "New Column")
                 (dom/input #js {:id "new-column" :name "new-column"
                                 :type "text"
                                 :value header
                                 :onKeyUp (fn [e]
                                            (when (= 13 (.-keyCode e))
                                              (create-column)))
                                 :onChange (fn [e]
                                             (om/set-state! owner :header
                                                            (.. e -target -value)))})
                 (dom/button #js {:onClick create-column}
                             "Add column"))))))

(defn create-note-button [app owner]
  (reify
    om/IInitState
    (init-state [_] {:text ""})
    om/IRenderState
    (render-state [this {:keys [text]}]
      (let [{:keys [connection column-id]} app
            create-note (fn []
                          (when (seq text)
                            (new-note (om/value connection) column-id text)
                            (om/set-state! owner :text "")))]
        (dom/div nil
                 (dom/label #js {:htmlFor "new-note"} "New Note")
                 (dom/input #js {:id "new-note" :name "new-note"
                                 :type "text"
                                 :value text
                                 :onKeyUp (fn [e]
                                            (when (= 13 (.-keyCode e))
                                              (create-note)))
                                 :onChange (fn [e]
                                             (om/set-state! owner :text
                                                            (.. e -target -value)))})
                 (dom/button #js {:onClick create-note}
                             "Add note"))))))

(defn delete-note-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id note-id]} app
            delete-note (fn []
                          (delete-note (om/value connection) column-id note-id))]
        (dom/button #js {:onClick delete-note}
                    "Delete!")))))

(defn create-vote-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id note-id]} app
            create-vote (fn []
                          (new-vote (om/value connection) column-id note-id))]
        (dom/button #js {:onClick create-vote}
                    "Upvote!")))))

(defn change-env [app env-id]
  (set! (.-pathname js/location) (str "e/" env-id)))

(defn create-environment-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection]} app
            create-env (fn []
                         (go
                          (let [env-id (<! (new-environment @connection))]
                            (change-env app env-id))))]
        (dom/button #js {:onClick create-env}
                    "New environment!")))))

(defn note-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id note]} app
            [id note] note]
        (dom/div #js {:className "note"}
                 (:text note) " "
                 (count (:votes note)) " votes!"
                 (om/build create-vote-button {:connection connection
                                               :column-id column-id
                                               :note-id id})
                 (om/build delete-note-button {:connection connection
                                               :column-id column-id
                                               :note-id id}))))))

(defn column-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column]} app
            [id column] column]
        (dom/div #js {:className "column"}
                 (dom/h1 nil (:header column))
                 (apply dom/div nil
                        (map (fn [note] (om/build note-view {:connection connection
                                                            :column-id id
                                                            :note note}))
                             (:notes column)))
                 (om/build create-note-button {:connection connection
                                               :column-id id}))))))

(defn view [app owner]
  (reify
    om/IInitState
    (init-state [_] {})
    om/IWillMount
    (will-mount [_]
      (let [action-chan (chan)]
        (if (:id app)
          (put! (get-in (if (om/rendering?) app @app) [:connection :to-send])
                {:cmd :register :action (:id app)}))
        (sub (get-in app [:connection :incoming]) :cmds action-chan)
        (go-loop []
                 (let [actions (:commands (<! action-chan))]
                   (om/transact! app :state (partial apply-actions actions))
                   (recur)))))
    om/IRenderState
    (render-state [this state]
      (let [connection (om/value (:connection app))
            columns (:state app)]
        (dom/div nil
                 (dom/div nil
                          (om/build create-environment-button app))
                 (if (:id app)
                   (dom/div nil
                            (om/build create-column-button (:connection app))
                            (apply dom/div nil
                                   (map (fn [col]
                                          (om/build column-view {:connection connection
                                                                 :column col}))
                                        columns)))))))))

(def app-state (atom {:state {} :connection (web-socket)}))

(defn get-env-id []
  (let [pathname (.-pathname js/location)
        env-id (second (re-find #"e/([0-9]+)" pathname))]
    (if env-id
      (js/parseInt env-id)
      nil)))

(defn setup! []
  (swap! app-state assoc :id (get-env-id))
  (om/root
   view
   app-state
   {:target (. js/document (getElementById "retroboard"))}))

(set! (.-onload js/window) setup!)
