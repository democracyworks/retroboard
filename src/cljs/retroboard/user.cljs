(ns retroboard.user
  (:require [retroboard.xhr :as xhr]
            [cljs.core.async :refer [<! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(defn do-login
  ([username password]
     (let [ch (chan)]
       (do-login username password ch)
       ch))
  ([username password ch]
     (xhr/edn-xhr
      {:method :post
       :url "http://localhost:8080/login"
       :data {:username username
              :password password}
       :chan ch})))

(defn fetch-boards
  ([]
     (let [ch (chan)]
       (fetch-boards ch)
       ch))
  ([ch]
     (xhr/edn-xhr
      {:method :get
       :url "http://localhost:8080/boards"
       :chan ch})))

(defn login-view [app owner {:keys [on-login]}]
  (reify
    om/IInitState
    (init-state [_]
      {:ch (chan)})
    om/IWillMount
    (will-mount [_]
      (let [ch (om/get-state owner :ch)]
        (go (while true
              (let [{:keys [status body]} (<! ch)]
                (if (= 200 status)
                  (on-login)))))))
    om/IRenderState
    (render-state [_ {:keys [ch]}]
      (dom/form #js {:id "login"}
                (dom/div nil
                         (dom/label #js {:htmlFor "username"}
                                    "Email")
                         (dom/input #js {:type "text"
                                         :id "username"
                                         :ref "username"}))
                (dom/div nil
                         (dom/label #js {:htmlFor "password"}
                                    "Password")
                         (dom/input #js {:type "password"
                                         :id "password"
                                         :ref "password"}))
                (dom/button
                 #js {:onClick (fn [e]
                                 (.preventDefault e)
                                 (do-login (.-value (om/get-node owner "username"))
                                           (.-value (om/get-node owner "password"))
                                           ch))}
                 "Login")))))
(defn board-link [board-id]
  (dom/a #js {:href (str "/e/" board-id)}
         board-id))

(defn profile-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:boards nil
       :logged-in? false
       :ch (chan)})
    om/IWillMount
    (will-mount [_]
      (let [ch (om/get-state owner :ch)]
        (fetch-boards ch)
        (go (while true
              (let [{:keys [status body]} (<! ch)]
                (om/set-state! owner :logged-in? (= 200 status))
                (om/set-state! owner :boards body))))))
    om/IRenderState
    (render-state [_ {:keys [logged-in? boards ch]}]
      (dom/div nil
               (dom/h1 nil "Logged in? " (pr-str logged-in?))
               (if logged-in?
                 (dom/div nil
                          (dom/div nil
                                   (dom/h1 nil "Your Boards")
                                   (apply dom/ul nil
                                          (map #(dom/li nil (board-link %)) boards)))
                          (dom/a #js {:href "/logout"} "Logout"))
                 (dom/div nil
                          (om/build login-view app {:opts {:on-login #(fetch-boards ch)}})))))))
