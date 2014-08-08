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
       :url "http://localhost:8080/login"
       :data {:username email
              :password password}
       :chan ch})))

(defn signup
  ([email password name]
     (let [ch (chan)]
       (signup email password name ch)
       ch))
  ([email password name ch]
     (xhr/edn-xhr
      {:method :post
       :url "http://localhost:8080/signup"
       :data {:email email
              :password password
              :name name}})))

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

(defn input [owner id placeholder & type]
  (dom/input #js {:type (or (first type) "text")
                  :id (name id)
                  :onChange (fn [e]
                              (om/set-state! owner id
                                             (.. e -target -value)))
                  :placeholder placeholder}))

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
                (if (= 200 status)
                  (on-login)))))))
    om/IRenderState
    (render-state [_ {:keys [ch screen email password name]}]
      (dom/div #js {:id "login-signup"}
               (dom/form #js {:id "login"
                              :style (display (= screen :login))}
                         (input owner :email "Your email")
                         (input owner :password "Your password" "password")
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
                         (input owner :name "Your name")
                         (input owner :email "Your email")
                         (input owner :password "Choose a password" "password")
                         (dom/button
                          #js {:onClick (fn [e]
                                          (.preventDefault e)
                                          (signup email
                                                  password
                                                  name
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
                          (dom/a #js {:href "/logout"} "Logout"))
                 (dom/div nil
                          (om/build login-view app {:opts {:on-login #(fetch-boards ch)}})))))))
