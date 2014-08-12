(ns retroboard.user
  (:require [retroboard.xhr :as xhr]
            [retroboard.util :refer [display]]
            [cljs.core.async :refer [<! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(defn do-login
  ([email password]
     (let [ch (chan)]
       (do-login email password ch)
       ch))
  ([email password ch]
     (xhr/edn-xhr
      {:method :post
       :url "/user/login"
       :data {:username email
              :password password}
       :chan ch})))

(defn signup
  ([username email password]
     (let [ch (chan)]
       (signup username email password ch)
       ch))
  ([username email password ch]
     (xhr/edn-xhr
      {:method :post
       :url "/user/signup"
       :data {:username username
              :email email
              :password password}
       :chan ch})))

(defn add-board
  ([eid]
     (let [ch (chan)]
       (add-board eid ch)
       ch))
  ([eid ch]
     (xhr/edn-xhr
      {:method :post
       :url "/user/boards/add"
       :data {:eid eid}
       :chan ch})))

(defn fetch-boards
  ([]
     (let [ch (chan)]
       (fetch-boards ch)
       ch))
  ([ch]
     (xhr/edn-xhr
      {:method :get
       :url "/user/boards"
       :chan ch})))

(defn input [id placeholder on-change & [type]]
  (dom/input #js {:type (or type "text")
                  :id (name id)
                  :onChange on-change
                  :placeholder placeholder}))

(defn handle-change [owner id]
  (fn [e]
    (let [new-value (.. e -target -value)]
      (om/set-state! owner id new-value))))
(defn login-view [app owner {:keys [on-login]}]
  (reify
    om/IInitState
    (init-state [_]
      {:screen :login
       :ch (chan)})
    om/IWillMount
    (will-mount [_]
      (let [ch (om/get-state owner :ch)]
        (go (while true
              (let [{:keys [status body]} (<! ch)]
                (if (<= 200 status 300)
                  (on-login)))))))
    om/IRenderState
    (render-state [_ {:keys [ch screen username email password]}]
      (dom/div #js {:id "login-signup"}
               (dom/form #js {:id "login"
                              :style (display (= screen :login))}
                         (input :email "Your username or email"
                                (handle-change owner :email))
                         (input :password "Your password"
                                (handle-change owner :password)
                                "password")
                         (dom/button
                          #js {:onClick (fn [e]
                                          (.preventDefault e)
                                          (do-login email
                                                    password
                                                    ch))}
                          "Login")
                         (dom/a #js {:href "#"
                                     :onClick (fn [_] (om/set-state! owner :screen :signup))}
                                "or sign up"))
               (dom/form #js {:id "signup"
                              :style (display (= screen :signup))}
                         (input :username "Choose a username"
                                (handle-change owner :username))
                         (input :email "Your email"
                                (handle-change owner :email))
                         (input :password "Choose a password"
                                (handle-change owner :password)
                                "password")
                         (dom/button
                          #js {:onClick (fn [e]
                                          (.preventDefault e)
                                          (signup username
                                                  email
                                                  password
                                                  ch))}
                          "Signup")
                         (dom/a #js {:href "#"
                                     :onClick (fn [_] (om/set-state! owner :screen :login))}
                                "or login"))))))

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
                          (dom/a #js {:href "/user/logout"} "Logout"))
                 (dom/div nil
                          (om/build login-view app {:opts {:on-login #(fetch-boards ch)}})))))))
