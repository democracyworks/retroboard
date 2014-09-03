(ns retroboard.user
  (:require [retroboard.util :refer [mongo-uri]]
            [cemerick.friend.credentials :as creds]
            [monger.core :as mg]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

(defn db [] (:db (mg/connect-via-uri (mongo-uri))))
(def users "users")

(def keywordize-roles
  (comp set (partial map #(keyword "retroboard.user" %))))

(defn lookup [email]
  (if-let [user (mc/find-one-as-map (db) users
                                    {:email email})]
    (-> user
        (update-in [:roles] keywordize-roles)
        (assoc :username (:email user)))))

(defn email? [email]
  (and email (re-find  #".+@.+\..*" email)))

(defn valid-email? [email]
  (and (email? email)
       (not (mc/any? (db) users {:email email}))))

(defn valid-password? [password]
  (> (count password) 6))

(defn new-user [email password]
  {:email email
   :password (creds/hash-bcrypt password)
   :boards #{}
   :roles #{::user}
   :_id (ObjectId.)})

(defn add-user [email password]
  (when (and (valid-email? email)
             (valid-password? password))
    (mc/insert-and-return (db) users (new-user email password))))

(defn delete-user [email]
  (mc/remove (db) users {:email email}))

(defn cred-fn [creds]
  (creds/bcrypt-credential-fn lookup creds))

(defn add-board [email board]
  (let [user (lookup email)]
    (mc/update-by-id (db) users
                     (:_id user)
                     (update-in user [:boards] (comp set #(conj % board))))))

(defn boards [email]
  (:boards (lookup email)))
