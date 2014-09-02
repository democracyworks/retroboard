(ns retroboard.user
  (:require [retroboard.xhr :as xhr]
            [retroboard.util :refer [display]]
            [retroboard.actions :as actions]
            [retroboard.templates :as templates]
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

(defn validate-input [validation email password owner]
  (let [email-validation (and (re-find #"\." email)
                              (re-find #"@" email))
        password-validation (> (count password) 7)
        validation {}]
    (if email-validation
      (if password-validation
        true
        (om/set-state! owner :validation (assoc validation :password "Your password is too short.")))
      (om/set-state! owner :validation (assoc validation :email "Please enter a valid email.")))))

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

(defn input [id type on-change validation]
  (let [email-error (:email validation)
        passw-error (:password validation)]
    (dom/div #js {:className "form-group"}
             (dom/label #js {:htmlFor id
                             :className "col-sm-3 col-xs-12 control-label"}
                        type)
             (dom/div #js {:className "col-sm-9 col-xs-12"}
                      (dom/input #js {:className "form-control required"
                                      :type type
                                      :name id
                                      :id id
                                      :placeholder ""
                                      :onChange on-change})
                      (if (= "email" type)
                        (dom/label #js {:htmlFor id
                                        :className "error"
                                        :style (display (if email-error true))}
                                   email-error)
                        (dom/label #js {:htmlFor id
                                        :className "error"
                                        :style (display (if passw-error true))}
                                   passw-error))))))

(defn handle-change [owner id]
  (fn [e]
    (let [new-value (.. e -target -value)]
      (om/set-state! owner id new-value))))

(defn board-link [board-id]
  (dom/a #js {:href (str "/e/" board-id)}
         (str "http://remboard.com/e/" board-id)))

(defn header-nav [& [options]]
  (dom/nav #js {:className "navigation navigation-header"}
           (dom/div #js {:className "container"}
                    (dom/div #js {:className "navigation-brand"}
                             (when (:logo options)
                               (dom/div #js {:className "brand-logo"}
                                        (dom/a #js {:href "#" :className "logo"})
                                        (dom/span #js {:className "sr-only"} "remboard"))))
                    (dom/div #js {:className "navigation-navbar"}
                             (dom/ul #js {:className "navigation-bar navigation-bar-right"}
                                     (dom/li nil
                                             (dom/a #js {:href "#"} "Login"))
                                     (dom/li #js {:className "featured"}
                                             (dom/a #js {:href "#"} "Sign Up")))))))

(defn header [& [options]]
  (dom/header nil
              (dom/div #js {:className "header-holder"}
                       (header-nav options))))

(defn login-view [app owner {:keys [on-login]}]
  (reify
    om/IInitState
    (init-state [_]
      {:screen :signup
       :ch (chan)})
    om/IWillMount
    (will-mount [_]
      (let [ch (om/get-state owner :ch)]
        (go (while true
              (let [{:keys [status body]} (<! ch)]
                (if (<= 200 status 300)
                  (on-login)
                  ))))))
    om/IRenderState
    (render-state [_ {:keys [ch screen username email password validation boards status] :as state}]
      (dom/div #js {:id "login-signup"}
               (dom/form #js {:className "form form-register dark animated fadeIn"
                              :style (display (= screen :login))}
                         (input "your_email" "email"
                                (handle-change owner :email) validation)
                         (input "your_password" "password"
                                (handle-change owner :password) validation)
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
                                "or register"))
               (dom/form #js {:className "form form-register dark animated fadeIn"
                              :style (display (= screen :signup))}
                         (input "email" "email"
                                (handle-change owner :email) validation)
                         (input "password" "password"
                                (handle-change owner :password) validation)
                         (dom/button
                          #js {:className "btn btn-primary btn-lg btn-block"
                               :onClick (fn [e]
                                          (.preventDefault e)
                                          (when (validate-input validation email password owner)
                                            (do (signup email
                                                        password
                                                        ch)
                                                (do-login email
                                                          password
                                                          ch))))}
                          "Register")
                         (dom/a #js {:href "#"
                                     :id "switch-login-register"
                                     :onClick (fn [_] (om/set-state! owner :screen :login))}
                                "or login"))))))

(defn display-boards [boards]
  (apply dom/div nil
         (map #(dom/p #js {:className "text-center"}
                      (board-link %)) (reverse (take 3 boards)))))

(defn create-environment
  ([& [initial-actions]]
     (let [create-ch (chan)]
       (xhr/edn-xhr
        {:method :post
         :url "/env"
         :data {:initial-actions initial-actions
                :on-join (actions/user-join)
                :on-leave (actions/user-leave)}
         :chan create-ch})
       (go (:body (<! create-ch))))))

(defn change-env [env-id]
  (set! (.-pathname js/location) (str "e/" env-id)))

(defn create-board-button
  ([title]
     (create-board-button title nil))
  ([title template]
     (let [create-env (fn []
                        (go
                         (let [env-id (<! (create-environment template))]
                           (change-env env-id))))]
       (dom/button
        #js {:className "new-environment btn btn-secondary btn-block"
             :onClick create-env}
        title))))

(defn dashboard-view [app owner {:keys [on-login]}]
  (reify
    om/IInitState
    (init-state [_]
      {:ch (chan)})
    om/IWillMount
    (will-mount [_]
      (let [ch (om/get-state owner :ch)]
        (fetch-boards ch)
        (go (while true
              (let [{:keys [status body]} (<! ch)]
                (om/set-state! owner :boards body)
                (if (<= 200 status 300)
                  (on-login)))))))
    om/IRenderState
    (render-state [_ {:keys [ch boards status] :as state}]
      (dom/div #js {:id "login-signup"}
               (dom/form #js {:className "form form-register dark animated fadeIn"
                              }
                         (dom/section #js {:id "sc-boxalert"}
                                      (dom/div #js {:className "text-left"}
                                               (dom/div #js {:className "alert alert-info"}
                                                        (dom/strong nil "Congrats! ")
                                                        "Your login was successful."
                                                        (dom/i #js {:className "icon icon-badges-votes-10"})))
                                      (dom/div nil
                                               (dom/h3 #js {:className "text-center"}
                                                       "Your Latest Remboards")
                                               (.log js/console boards)
                                               (display-boards boards)))
                         (dom/h3 #js {:className "text-center"}
                                 "Create a New Board")
                         (create-board-button "Pro / Con" templates/pros-and-cons)
                         (create-board-button "Card Wall" templates/card-wall)
                         (create-board-button "Retro" templates/retro)
                         (create-board-button "Empty Board")
                         (dom/a #js {:id "switch-login-register"
                                     :href "/user/logout"} "or logout"))))))

(defn register-content [app ch logged-in?]
  (dom/div #js {:id "hero"
                :className "static-header light"}
           (dom/div #js {:className "text-heading animated fadeIn"}
                    (dom/h1 nil
                            "Collaborate "
                            (dom/span #js {:className "highlight"} "remotely")
                            ", Create Together")
                    (dom/p nil "remboard is your online collaboration spot for reviewing, brainstorming and more"))
           (dom/div #js {:className "container"}
                    (dom/div #js {:className "row"}
                             (dom/div #js {:className "col-lg-6 col-lg-offset-3 col-md-8 col-md-offset-2 col-sm-10 col-sm-offset-1 col-xs-12"}
                                      (if logged-in?
                                        (om/build dashboard-view app)
                                        (om/build login-view app {:opts {:on-login #(fetch-boards ch)}}))
                                      (dom/p #js {:className "agree-text animated fadeIn"}
                                             (dom/a #js {:href "#"
                                                         :onClick (fn []
                                                                    (go
                                                                     (let [env-id (<! (create-environment))]
                                                                       (change-env env-id))))}
                                                    "Or, just go straight to an empty board!")))))))

(def back-to-top
  (dom/div #js {:className "back-to-top"}
           (dom/i #js {:className "fa fa-angle-up fa-3x"})))

(defn register-view [app ch logged-in?]
  (dom/div #js {:id "register-page"}
           (header {:logo true})
           (register-content app ch logged-in?)
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
                (om/set-state! owner :logged-in? (= 200 status)))))))
    om/IRenderState
    (render-state [_ {:keys [logged-in? ch]}]
      (register-view app ch logged-in?))))
