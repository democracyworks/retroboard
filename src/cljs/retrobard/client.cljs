(ns retroboard.client
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [retroboard.config :as config]
            [retroboard.resource :refer [temprid]]
            [cljs.core.async :refer [chan <! put! pub sub unsub]]
            [goog.events :as events]
            [cljs.reader :as reader]
            [clojure.string :refer [split]])
  (:require-macros [retroboard.macros :refer [defactions]]
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



(defactions apply-action
  (new-column [id header state]
              (assoc state id {:header header :notes {}}))
  (delete-column [id state]
                 state (dissoc state id))
  (new-note [id column-id text state]
            (assoc-in state [column-id :notes id] {:text text :votes #{}}))
  (delete-note [column-id id state]
               (update-in state [column-id :notes] dissoc id))
  (new-vote [id column-id note-id state]
            (update-in state [column-id :notes note-id :votes] conj id))
  (edit-note [id column-id new-text state]
             (assoc-in state [column-id :notes id :text] new-text)))

(defn apply-actions [actions initial-state]
  (reduce (fn [state action]
            ((apply-action action) state))
          initial-state actions))


(defn column-placeholder []
  (str "Add a Column (e.g. "
       (first (shuffle ["What Went Well"
                        "What Needs Improvement"
                        "Action Items"
                        "Keep Doing"
                        "Start Doing"
                        "Stop Doing"]))
       ")"))

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
        (dom/div #js {:id "create-column"}
                 (dom/input #js {:id "new-column" :name "new-column"
                                 :type "text"
                                 :placeholder (column-placeholder)
                                 :value header
                                 :onKeyUp (fn [e]
                                            (when (= 13 (.-keyCode e))
                                              (create-column)))
                                 :onChange (fn [e]
                                             (om/set-state! owner :header
                                                            (.. e -target -value)))})
                 (dom/span #js {:onClick create-column
                                :className "add-column"
                                :disabled (empty? header)}))))))

(defn delete-column-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id]} app
            delete-column (fn []
                            (delete-column (om/value connection) column-id))]
        (dom/div #js {:onClick delete-column
                         :className "delete-column"}
                    "Delete Column")))))

(defn note-placeholder []
  (first (shuffle ["I just think that..."
                   "What if we..."
                   "Why do we always..."
                   "Maybe next time we could..."
                   "I like that we..."
                   "We're doing better about..."])))

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
                 (dom/textarea #js {:id "new-note" :name "new-note"
                                    :placeholder (note-placeholder)
                                    :type "text"
                                    :value text
                                    :onKeyUp (fn [e]
                                               (when (= 13 (.-keyCode e))
                                                 (create-note)))
                                    :onChange (fn [e]
                                                (om/set-state! owner :text
                                                               (.. e -target -value)))})
                 (dom/div #js {:onClick create-note
                               :className "add-note"
                               :disabled (empty? text)}
                          "Add note"))))))

(defn delete-note-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id note-id]} app
            delete-note (fn []
                          (delete-note (om/value connection) column-id note-id))]
        (dom/div #js {:onClick delete-note
                         :className "delete-note"}
                    "✖")))))

(defn create-vote-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id note-id]} app
            create-vote (fn []
                          (new-vote (om/value connection) (temprid) column-id note-id))]
        (dom/div #js {:onClick create-vote
                         :className "vote"}
                    "✚")))))

(defn change-env [env-id]
  (set! (.-pathname js/location) (str "e/" env-id)))

(defn create-environment-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection]} app
            create-env (fn []
                         (go
                          (let [env-id (<! (new-environment @connection))]
                            (change-env env-id))))]
        (dom/button #js {:onClick create-env
                         :className "new-environment"}
                    "New Environment")))))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn handle-change [e data edit-key owner]
  (om/transact! data edit-key (fn [_] (.. e -target -value))))

(defn end-edit [text owner cb]
  (om/set-state! owner :editing false)
  (cb text))

(defn editable [data owner {:keys [edit-key on-edit] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:editing false})
    om/IRenderState
    (render-state [_ {:keys [editing]}]
      (let [text (get data edit-key)]
        (dom/div #js {:className "note-content"}
                (dom/p #js {:style (display (not editing))
                            :onClick (fn [el] (om/set-state! owner :editing true))}
                          text)
                (dom/textarea
                 #js {:className "edit-content-input"
                      :style (display editing)
                      :value text
                      :onChange #(handle-change % data edit-key owner)
                      :onKeyUp #(case (.-keyCode %)
                                     13 (end-edit text owner on-edit)
                                     nil)
                      :onBlur (fn [e]
                                (when (om/get-state owner :editing)
                                  (end-edit text owner on-edit)))})
                (dom/div
                 #js {:className "edit-note-button"
                      :style (display (not editing))
                      :onClick #(om/set-state! owner :editing true)}
                 "Edit"))))))

(defn note-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id note]} app
            [id note] note]
        (dom/div #js {:className "note-wrapper"}
                 (dom/div {:className "note"}
                          (om/build editable note
                                    {:opts {:edit-key :text
                                            :on-edit (partial edit-note connection id column-id)}}))
                 (dom/div #js {:className "vote-delete-row"}
                          (om/build create-vote-button {:connection connection
                                                        :column-id column-id
                                                        :note-id id})
                          (dom/div #js {:className "votes"}
                                   "+ " (count (:votes note)))
                          (om/build delete-note-button {:connection connection
                                                        :column-id column-id
                                                        :note-id id})))))))

(defn column-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column]} app
            [id column] column]
        (dom/div #js {:className "column"}
                 (dom/h1 nil (:header column))
                 (om/build create-note-button {:connection connection
                                               :column-id id})                 
                 (apply dom/div nil
                        (map (fn [note] (om/build note-view {:connection connection
                                                            :column-id id
                                                            :note note}))
                             (sort-by first (:notes column))))
                 (om/build delete-column-button {:connection connection
                                                 :column-id id}))))))


(defn error-handler [app]
  (let [error-chan (chan)]
    (sub (get-in app [:connection :incoming]) :error error-chan)
    (go-loop []
             (let [error (:error (<! error-chan))]
               (case error
                 :no-such-environment (om/update! app :connected :no-such-environment))))))

(defn view [app owner]
  (reify
    om/IInitState
    (init-state [_] {})
    om/IWillMount
    (will-mount [_]
      (let [action-chan (chan)]
        (error-handler app)
        (if (:id app)
          (put! (get-in (if (om/rendering?) app @app) [:connection :to-send])
                {:cmd :register :action (:id app)}))
        (sub (get-in app [:connection :incoming]) :cmds action-chan)
        (go-loop []
                 (let [actions (:commands (<! action-chan))]
                   (om/update! app :connected :connected)
                   (om/transact! app :state (partial apply-actions actions))
                   (recur)))))
    om/IRenderState
    (render-state [this state]
      (let [connection (om/value (:connection app))
            columns (:state app)]
        (dom/div nil
                 (if (:id app)
                   (case (:connected app)
                     nil
                     (dom/h3 nil "Connecting...")
                     :no-such-environment
                     (dom/h3 nil "Sorry. That environment doesn't exist. Why not make a new one?")
                     :connected
                     (dom/div #js {:className "board"}
                              (om/build create-column-button (:connection app))
                              (apply dom/div #js {:id "columns"}
                                     (map (fn [col]
                                            (om/build column-view {:connection connection
                                                                   :column col}))
                                          (sort-by first columns)))))
                   (dom/div nil
                            (om/build create-environment-button app))))))))

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
