(ns retroboard.api.user
  (:require [retroboard.user :as user]
            [retroboard.util :refer [edn-resp]]
            [compojure.core :refer [routes GET POST ANY]]
            [ring.util.response :as resp]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]))


(defn boards [req]
  (friend/authorize #{::user/user}
                    (edn-resp (user/boards (:current (friend/identity req))))))

(defn add-board [req]
  (let [{:keys [eid]} (:params req)]
    (friend/authorize #{::user/user}
                      (user/add-board (:current (friend/identity req)) eid)
                      (edn-resp "OK"))))

(defn signup [req]
  (let [{:keys [email password]} (:params req)
        user (user/add-user email password)]
    (if user
      (friend/merge-authentication (edn-resp "Created!" 201)
                                   (assoc user :identity email))
      (edn-resp {:errors {:email "That email didn't work."}} 402))))

(def api-routes
  (->
   (routes
    (GET "/boards" [] boards)
    (POST "/boards/add" [] add-board)
    (POST "/signup" [] signup)
    (friend/logout (ANY "/logout" request (resp/redirect "/"))))
   (friend/authenticate
    {:login-uri "/login"
     :default-landing-uri "/"
     :unauthenticated-handler (fn [_] (edn-resp "You need to login to see that." 401))
     :credential-fn user/cred-fn
     :workflows [(workflows/interactive-form
                  :login-failure-handler (fn [_] (edn-resp "Invalid email or password" 401))
                  :redirect-on-auth? false)]})))
