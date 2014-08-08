(ns retroboard.user
  (:require [cemerick.friend.credentials :as creds]))

(def all (atom {"friend" {:username "friend"
                          :password (creds/hash-bcrypt "clojure")
                          :boards #{}
                          :roles #{::user}}
                "friend-admin" {:username "friend-admin"
                                :password (creds/hash-bcrypt "clojure")
                                :boards #{}
                                :roles #{::admin}}}))

(defn cred-fn [creds]
  (creds/bcrypt-credential-fn @all creds))

(defn add-board [username eid]
  (swap! all update-in [username :boards] conj eid))

(defn boards [username]
  (get-in @all [username :boards]))
