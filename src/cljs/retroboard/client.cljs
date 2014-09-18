(ns retroboard.client
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [retroboard.xhr :as xhr]
            [retroboard.user :as user]
            [retroboard.actions :as a]
            [retroboard.util :refer [display translate3d]]
            [retroboard.resource :refer [temprid]]
            [cljs.core.async :refer [chan <! put! pub sub unsub] :as async]
            [goog.events :as events]
            [cljs.reader :as reader]
            [clojure.string :refer [split]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:import goog.net.WebSocket
           goog.net.WebSocket.EventType))

(enable-console-print!)

(def ws-url (str "ws://" (.. js/window -location -host) "/env"))

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
    {:to-send to-send :incoming (pub incoming :cmd) :websocket ws
     :connect (fn [] (.open ws ws-url))}))

(defn apply-actions [actions initial-state]
  (reduce (fn [state action]
            ((a/apply-action action) state))
          initial-state actions))


(defn create-column-button [connection owner]
  (reify
    om/IInitState
    (init-state [_] {:header ""})
    om/IRenderState
    (render-state [this {:keys [header]}]
      (letfn [(create-column []
                (when (seq header)
                  (a/new-column (om/value connection) (temprid) header)
                  (om/set-state! owner :header "")))]
        (dom/div #js {:id "create-column"}
                 (dom/input #js {:id "new-column" :name "new-column"
                                 :type "text"
                                 :placeholder "Add a Column"
                                 :value header
                                 :onKeyUp (fn [e]
                                            (when (= 13 (.-keyCode e))
                                              (create-column)))
                                 :onChange (fn [e]
                                             (om/set-state! owner :header
                                                            (.. e -target -value)))}))))))

(defn delete-column-button [app owner]
  (reify
    om/IInitState
    (init-state [_] {:deleting-column false})
    om/IRenderState
    (render-state [this {:keys [deleting-column]}]
      (let [{:keys [connection column-id]} app
            begin-delete-column (fn []
                                  (om/set-state! owner :deleting-column true))
            end-delete-column (fn []
                                (om/set-state! owner :deleting-column false))
            delete-column (fn []
                            (a/delete-column (om/value connection) column-id)
                            (end-delete-column))]
        (dom/div nil
                 (dom/div #js {:onClick begin-delete-column
                               :style (display (not deleting-column))
                               :className "delete-column"}
                          "Delete Column")
                 (dom/div #js {:style (display deleting-column)
                               :className "are-you-sure"}
                          (dom/span nil "Are You Sure?")
                          (dom/div #js {:onClick delete-column
                                        :className "confirm-delete"}
                                   "Yes")
                          (dom/div #js {:onClick end-delete-column
                                        :className "cancel-delete"}
                                   "No")))))))

(defn create-note-button [app owner]
  (reify
    om/IInitState
    (init-state [_] {:text ""})
    om/IRenderState
    (render-state [this {:keys [text]}]
      (let [{:keys [connection column-id]} app
            create-note (fn []
                          (when (seq text)
                            (a/new-note (om/value connection) (temprid) column-id text)
                            (om/set-state! owner :text "")))]
        (dom/div #js {:className "new-note-wrapper"}
                 (dom/textarea #js {:className "new-note" :name "new-note"
                                    :type "text"
                                    :value text
                                    :onKeyDown (fn [e]
                                                 (when (= 13 (.-keyCode e))
                                                   (create-note)
                                                   (.preventDefault e)))
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
                          (a/delete-note (om/value connection) column-id note-id))]
        (dom/div #js {:onClick delete-note
                      :className "delete-note"})))))

(defn create-vote-button [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id note-id]} app
            create-vote (fn []
                          (a/new-vote (om/value connection) (temprid) column-id note-id))]
        (dom/div #js {:onClick create-vote
                      :className "vote"})))))

(defn handle-change [e data edit-key]
  (om/transact! data edit-key (fn [_] (.. e -target -value))))

(defn end-edit [text owner cb]
  (om/set-state! owner :editing false)
  (cb text))

(defn focus-and-set-cursor [textarea]
  (let [val (.-value textarea)]
    (.focus textarea)
    (set! (.-value textarea) "")
    (set! (.-value textarea) val)))

;;;DRAGGING
(def mouse-move-ch
  (chan (async/sliding-buffer 1)))

(def mouse-down-ch
  (chan (async/sliding-buffer 1)))

(def mouse-up-ch
  (chan (async/sliding-buffer 1)))

(def mouse-move-mult
  (async/mult mouse-move-ch))

(def mouse-down-mult
  (async/mult mouse-down-ch))

(def mouse-up-mult
  (async/mult mouse-up-ch))

(defn on-node? [node x y]
  (let [rect (.getBoundingClientRect node)]
    (and (< (.-left rect) x (+ (.-left rect) (.-width rect)))
         (< (.-top rect) y (+ (.-top rect) (.-height rect))))))

(defn targeted-node [droppable-nodes x y]
  (if-let [drop-node (first (filter #(on-node? % x y) (keys droppable-nodes)))]
    (droppable-nodes drop-node)))

(js/window.addEventListener "mousedown" #(put! mouse-down-ch %))
(js/window.addEventListener "mouseup"   #(put! mouse-up-ch   %))
(js/window.addEventListener "mousemove" #(put! mouse-move-ch %))

(defprotocol IDragStart
  (drag-start [this event]))

(defprotocol IDragMove
  (drag-move [this event]))

(defprotocol IDragEnd
  (drag-end [this event]))

(defn -drag-start [owner event]
  (let [node (om/get-node owner)
        rel-x (+ (.-offsetLeft node) (- (.-pageX event) (.-offsetLeft node)))
        rel-y (+ (.-offsetTop node) (- (.-pageY event) (.-offsetTop node)))
        move (async/tap mouse-move-mult (chan (async/sliding-buffer 5)))
        up (async/tap mouse-up-mult (chan))
        dragger (om/get-state owner :dragger)]
    (go (while true
          (alt!
           move ([ev] (drag-move dragger ev))
           up ([ev] (drag-end dragger ev)))))
    (om/set-state! owner :move-ch move)
    (om/set-state! owner :up-ch up)
    (om/set-state! owner :state :mousedown)
    (om/set-state! owner :rel-x rel-x)
    (om/set-state! owner :rel-y rel-y)))

(defn -drag-end [owner event & [leave?]]
  (let [node (om/get-node owner)
        move (om/get-state owner :move-ch)
        up (om/get-state owner :up-ch)]
    (async/untap mouse-move-mult move)
    (async/untap mouse-up-mult up)
    (when-not leave?
      (om/set-state! owner :state nil))))

(defn free-drag [owner drop-fn droppable-nodes]
  (reify
    IDragStart
    (drag-start [_ event]
      (-drag-start owner event))
    IDragMove
    (drag-move [_ event]
      (let [rel-y (om/get-state owner :rel-y)
            rel-x (om/get-state owner :rel-x)
            new-y (- (.. event -pageY) rel-y)
            new-x (- (.. event -pageX) rel-x)
            node (om/get-node owner)
            style (.-style node)]
        (om/set-state! owner :x-value new-x)
        (om/set-state! owner :y-value new-y))
      (if (= :mousedown (om/get-state owner :state))
        (om/set-state! owner :state :dragging)))
    IDragEnd
    (drag-end [_ event]
      (if-let [drop-data (targeted-node @droppable-nodes (.-pageX event) (.-pageY event))]
        (-drag-end owner event (drop-fn drop-data))
        (-drag-end owner event)))))


(defn perform-drop [connection [drag-type drag-data] [drop-type drop-data]]
  (case [drag-type drop-type]
    [:note :column]
    (if (not= (:column-id drag-data) drop-data)
      (do (a/move-note connection (:note drag-data) (:column-id drag-data) drop-data)
          true))))



(defn draggable [{:keys [drop-fn comp-fn state drag-data droppable-nodes] :as data} owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:x-value 0
       :y-value 0
       :dragger (free-drag owner (partial drop-fn drag-data) droppable-nodes)})
    om/IWillMount
    (will-mount [_])
    om/IDidMount
    (did-mount [_]
      (let [comp (om/get-node owner)
            dragger (om/get-state owner :dragger)]
        (set! (.-onmousedown comp) (fn [ev]
                                     (drag-start dragger ev)))))
    om/IRenderState
    (render-state [this s]
      (let [is-dragging (= (:state s) :dragging)]
        (dom/div #js {:className (str "draggable" (if is-dragging " dragging"))
                      :style (translate3d is-dragging
                                          (om/get-state owner :x-value)
                                          (om/get-state owner :y-value))}
                 (om/build comp-fn state {:opts opts}))))))

(defn droppable [{:keys [droppable-nodes comp-fn state drop-data]} owner {:keys [] :as opts}]
  (reify
    om/IDidMount
    (did-mount [_]
      (om/transact! droppable-nodes #(assoc % (om/get-node owner) drop-data)))
    om/IWillUnmount
    (will-unmount [_]
      (om/transact! droppable-nodes #(dissoc % (om/get-node owner))))
    om/IRender
    (render [_]
      (om/build comp-fn state {:opts opts}))))

;;;DRAGGING

(defn begin-edit [owner cur-value]
  (set! (.-height (.-style (om/get-node owner "input")))
        (+ 15 (.-clientHeight (om/get-node owner "text"))))
  (om/set-state! owner :edit-text cur-value)
  (om/set-state! owner :editing true))

(defn editable [data owner {:keys [edit-key on-edit element wrap-class input-type] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:editing false
       :edit-text nil})
    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (when (and (om/get-state owner :editing)
                 (not (:editing prev-state)))
        (focus-and-set-cursor (om/get-node owner "input"))))
    om/IRenderState
    (render-state [this {:keys [editing edit-text]}]
      (let [text (get data edit-key)
            reset #(om/set-state! owner {:editing false
                                         :edit-text text})
            element (or element dom/p)]
        (dom/div #js {:className wrap-class}
                 (element #js {:style (display (not editing))
                               :ref "text"
                               :onClick #(begin-edit owner text)}
                          text)
                 ((or input-type dom/textarea)
                  #js {:className "edit-content-input"
                       :style (display editing)
                       :value edit-text
                       :ref "input"
                       :onChange #(om/set-state! owner :edit-text (.. % -target -value))
                       :onKeyDown (fn [e]
                                    (case (.-keyCode e)
                                      13 (do (handle-change e data edit-key)
                                             (end-edit edit-text owner on-edit)
                                             (.preventDefault e))
                                      27 (reset)
                                      nil))
                       :onBlur (fn [e]
                                 (when (om/get-state owner :editing)
                                   (reset)))}))))))

(defn note-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [connection column-id note]} app
            [id note] note]
        (dom/div #js {:className "note-wrapper"}
                 (dom/div #js {:className "note"}
                          (om/build editable note
                                    {:opts {:edit-key :text
                                            :wrap-class "note-content"
                                            :on-edit (partial a/edit-note connection id column-id)}}))
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
                 (om/build editable column
                           {:opts {:edit-key :header
                                   :wrap-class "column-header"
                                   :input-type dom/input
                                   :on-edit (partial a/edit-column-header connection id)
                                   :element dom/h1}})
                 (om/build create-note-button {:connection connection
                                               :column-id id})
                 (apply dom/div #js {:className "notes"}
                        (map (fn [note] (om/build draggable {:comp-fn note-view
                                                            :state {:connection connection
                                                                    :column-id id
                                                                    :note note}
                                                            :drop-fn (partial perform-drop connection)
                                                            :droppable-nodes (:droppable-nodes app)
                                                            :drag-data [:note {:column-id id :note (first note)}]}
                                                 {:react-key (first note)}))
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

(defn toggle-class [node classname]
  (.add (.-classList node) classname)
  (js/setTimeout #(.remove (.-classList node) classname) 500))

(defn copy-url-view [app owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [client (js/ZeroClipboard. (om/get-node owner))]
        (.on client "copy"
             (fn [e]
               (let [clipboard (.-clipboardData e)]
                 (.setData clipboard "text/plain" (.-href js/location)))))))
    om/IRender
    (render [_]
      (dom/span #js {:id "copy-board-url"
                     :title "Copy board url to share"}))))

(defn user-count-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:count (:user-count app)})
    om/IDidUpdate
    (did-update [_ next-props next-state]
      (let [old-count (om/get-state owner :count)
            new-count (:user-count app)]
        (cond (> old-count new-count)
              (toggle-class (om/get-node owner) "decrease")
              (< old-count new-count)
              (toggle-class (om/get-node owner) "increase"))
        (om/set-state! owner :count new-count)))
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:id "user-count"
                    :title (str (:user-count app) " people are in this board")}
               (:user-count app)))))

(defn back-or-home []
  (if (empty? (.-referrer js/document))
    (set! (.-location js/document) "/")
    (.back js/history)))

(defn view [app owner]
  (reify
    om/IInitState
    (init-state [_] {})
    om/IWillMount
    (will-mount [_]
      (let [action-chan (chan)
            eid (:id app)]
        (when eid
          ((get-in app [:connection :connect]))
          (error-handler app)
          (put! (get-in (if (om/rendering?) app @app) [:connection :to-send])
                {:cmd :register :action eid})
          (sub (get-in app [:connection :incoming]) :cmds action-chan)
          (go (let [actions (:commands (<! action-chan))]
                (om/update! app :connected :connected)
                (user/add-board eid)
                (go-loop [actions actions]
                         (om/transact! app :state (partial apply-actions actions))
                         (recur (:commands (<! action-chan)))))))))
    om/IRenderState
    (render-state [this state]
      (let [connection (om/value (:connection app))
            columns (get-in app [:state :columns])]
        (dom/div nil
                 (if (:id app)
                   (case (:connected app)
                     nil
                     (dom/h3 nil "Connecting...")
                     :no-such-environment
                     (dom/h3 nil "Sorry. That environment doesn't exist. Why not make a new one?")
                     :connected
                     (dom/div #js {:className "board"}
                              (dom/div #js {:id "header"}
                                       (dom/span #js {:id "back"
                                                      :onClick back-or-home})
                                       (om/build create-column-button (:connection app))
                                       (om/build editable (:state app)
                                                 {:opts {:edit-key :name
                                                         :input-type dom/input
                                                         :wrap-class "display-name"
                                                         :on-edit (partial a/edit-name connection)
                                                         :element dom/h1}})
                                       (om/build user-count-view (:state app))
                                       (om/build copy-url-view nil))
                              (apply dom/div #js {:id "columns"}
                                     (map (fn [col]
                                            (om/build droppable
                                                      {:comp-fn column-view
                                                       :droppable-nodes (:droppable-nodes app)
                                                       :state {:connection connection
                                                               :column col
                                                               :droppable-nodes (:droppable-nodes app)}
                                                       :drop-data [:column (first col)]}
                                                      {:react-key (first col)}))
                                          (sort-by first columns)))))
                   (om/build user/profile-view app)))))))

(def app-state (atom {:state a/new-board :connection (web-socket)
                      :droppable-nodes {}}))

(defn get-env-id []
  (let [pathname (.-pathname js/location)
        env-id (second (re-find #"e/(.+)" pathname))]
    env-id))

(defn setup! []
  (.config js/ZeroClipboard #js {:swfPath "/swf/ZeroClipboard.swf"})
  (swap! app-state assoc :id (get-env-id))
  (om/root
   view
   app-state
   {:target (. js/document (getElementById "retroboard"))}))

(set! (.-onload js/window) setup!)
