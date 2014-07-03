(ns retroboard.client
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [retroboard.config :as config]
            [retroboard.resource :refer [temprid]]
            [cljs.core.async :refer [chan <! put! pub sub unsub]]
            [goog.events :as events]
            [cljs.reader :as reader]
            [clojure.string :refer [split]])
  (:require-macros [retroboard.macros :refer [defaction]]
                   [cljs.core.async.macros :refer [go go-loop]])
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


(defaction new-column [id header]
  state (assoc state id {:header header :notes {}}))

(defaction delete-column [id]
  state (dissoc state id))

(defaction new-note [id column-id text]
  state (assoc-in state [column-id :notes id] {:text text :votes #{}}) )

(defaction delete-note [column-id id]
  state (update-in state [column-id :notes] dissoc id))

(defaction new-vote [id column-id note-id]
  state (update-in state [column-id :notes note-id :votes] conj id))

(defn apply-actions [actions initial-state]
  (reduce (fn [state action]
            ((apply-action action) state))
          initial-state actions))


(defn create-column-button [connection owner]
  (reify
    om/IInitState
    (init-state [_] {:header ""})
    om/IRenderState
    (render-state [this {:keys [header]}]
      (letfn [(create-column []
                (when (seq header)
                  (new-column (om/value connection) (temprid) header)
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

(defn delete-column-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id]} app
            delete-column (fn []
                            (delete-column (om/value connection) column-id))]
        (dom/button #js {:onClick delete-column}
                    "Delete!")))))

(defn create-note-button [app owner]
  (reify
    om/IInitState
    (init-state [_] {:text ""})
    om/IRenderState
    (render-state [this {:keys [text]}]
      (let [{:keys [connection column-id]} app
            create-note (fn []
                          (when (seq text)
                            (new-note (om/value connection) (temprid) column-id text)
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
                          (new-vote (om/value connection) (temprid) column-id note-id))]
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
                 (om/build delete-column-button {:connection connection
                                                 :column-id id})
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
