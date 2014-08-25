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
  ([email password]
     (let [ch (chan)]
       (signup email password ch)
       ch))
  ([email password ch]
     (xhr/edn-xhr
      {:method :post
       :url "/user/signup"
       :data {:username email
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

(defn input [id type on-change]
  (dom/div #js {:className "form-group"}
           (dom/label #js {:for id
                           :className "col-sm-3 col-xs-12 control-label"}
                      type)
           (dom/div #js {:className "col-sm-9 col-xs-12"}
                    (dom/input #js {:className "form-control required"
                                    :type type
                                    :name id
                                    :id id
                                    :placeholder ""
                                    :onChange on-change}))))

(defn handle-change [owner id]
  (fn [e]
    (let [new-value (.. e -target -value)]
      (om/set-state! owner id new-value))))

(defn board-link [board-id]
  (dom/a #js {:href (str "/e/" board-id)}
         board-id))

(defn header-nav [& [logo]]
  (dom/nav #js {:className "navigation navigation-header"}
           (dom/div #js {:className "container"}
                    (dom/div #js {:className "navigation-brand"}
                             (when logo
                               (dom/div #js {:className "brand-logo"}
                                        (dom/a #js {:href "#" :className "logo"})
                                        (dom/span #js {:className "sr-only"} "remboard")))
                             (dom/button #js {:className "navigation-toggle visible-xs"
                                              :type "button"
                                              :data-toggle "dropdown"
                                              :data-target "navigation-navbar"}
                                         (dom/span {:className "icon-bar"})
                                         (dom/span {:className "icon-bar"})
                                         (dom/span {:className "icon-bar"})))
                    (dom/div #js {:className "navigation-navbar"}
                             (dom/ul #js {:className "navigation-bar navigation-bar-right"}
                                     (dom/li nil
                                             (dom/a #js {:href "#"} "Login"))
                                     (dom/li #js {:className "featured"}
                                             (dom/a #js {:href "#"} "Sign Up")))))))

(defn header [& [logo]]
  (dom/header nil
              (dom/div #js {:className "header-holder"}
                       (header-nav logo))))

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
    (render-state [_ {:keys [ch screen username email password] :as state}]
      (dom/div #js {:id "login-signup"}
               (dom/form #js {:className "form form-register dark"
                              :style (display (= screen :login))}
                         (input "your_email" "email"
                                (handle-change owner :email))
                         (input "your_password" "password"
                                (handle-change owner :password))
                         (dom/button
                          #js {:className "btn btn-primary btn-lg btn-block"
                               :onClick (fn [e]
                                          (.preventDefault e)
                                          (do-login email
                                                    password
                                                    ch))}
                          "Login")
                         (dom/a #js {:href "#"
                                     :id "switch-login-register"
                                     :onClick (fn [_] (om/set-state! owner :screen :signup))}
                                "or sign up"))
               (dom/form #js {:className "form form-register dark"
                              :style (display (= screen :signup))}
                         (input "email" "email"
                                (handle-change owner :email))
                         (input "password" "password"
                                (handle-change owner :password))
                         (dom/button
                          #js {:className "btn btn-primary btn-lg btn-block"
                               :onClick (fn [e]
                                          (.preventDefault e)
                                          (signup email
                                                  password
                                                  ch))}
                          "Get Started")
                         (dom/a #js {:href "#"
                                     :id "switch-login-register"
                                     :onClick (fn [_] (om/set-state! owner :screen :login))}
                                  "or login"))))))

(defn register-content [app ch]
  (dom/div #js {:id "hero"
                :className "static-header light"}
           (dom/div #js {:className "text-heading"}
                    (dom/h1 nil
                            "Collaborate "
                            (dom/span #js {:className "highlight"} "remotely")
                            ", Create Together")
                    (dom/p nil "remboard is your online collaboration spot for reviewing, brainstorming and more"))
           (dom/div #js {:className "container"}
                    (dom/div #js {:className "row"}
                             (dom/div #js {:className "col-lg-6 col-lg-offset-3 col-md-8 col-md-offset-2 col-sm-10 col-sm-offset-1 col-xs-12"}
                                      (om/build login-view app {:opts {:on-login #(fetch-boards ch)}})
                                      (dom/p #js {:className "agree-text"}
                                             "By clicking you agree to our Terms of Service, Privacy Policy & Refund Policy."))))))

(def back-to-top
  (dom/div #js {:className "back-to-top"}
           (dom/i #js {:className "fa fa-angle-up fa-3x"})))

(def dashboard-content
  (dom/div #js {:id "body"}
           (dom/section #js {:id "sc-heading"
                             :className "section text-center"}
                        (dom/h1 nil
                                "Your "
                                (dom/span #js {:className "highlight"}
                                          "Rem")
                                "boards"))
           (dom/section #js {:id "sc-button"
                             :className "section dark text-center"}
                        (dom/div #js {:className "container"}
                                 (dom/h3 nil "Create a New Board")
                                 (dom/ul #js {:className "list-inline"}
                                         (dom/li nil
                                                 (dom/a #js {:className "btn btn-secondary"}
                                                        "Pro / Con"))
                                         (dom/li nil
                                                 (dom/a #js {:className "btn btn-primary"}
                                                        "Empty Board"))
                                         (dom/li nil
                                                 (dom/a #js {:className "btn btn-secondary"}
                                                        "Retro")))))))

(defn dashboard-view [app ch]
  (dom/div #js {:id "dashboard"}
           (header)
           dashboard-content))

(defn register-view [app ch]
  (dom/div #js {:id "register-page"}
           (header)
           (register-content app ch)
           back-to-top))

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
      (.log js/console logged-in?)
      (if logged-in?
        (dom/div #js {:id "shortcodes-page"}
                 (dashboard-view app ch)
                 #_(dom/div nil
                          (dom/h1 nil "Your Boards")
                          (apply dom/ul nil
                                 (map #(dom/li nil (board-link %)) boards)))
                 #_(dom/a #js {:href "/user/logout"} "Logout"))
        (register-view app ch)))))
