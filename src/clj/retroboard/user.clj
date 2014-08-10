(ns retroboard.user
  (:require [cemerick.friend.credentials :as creds]
            [monger.core :as mg]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

(def mongo-uri (or (System/getenv "MONGOHQ_URL") "mongodb://127.0.0.1/retroboard"))

(def db (:db (mg/connect-via-uri mongo-uri)))
(def users "users")

(defn username-or-email? [username-or-email]
  (if (and username-or-email (re-find #"@" username-or-email))
    :email
    :username))

(defn lookup-cond [username-or-email]
  {(username-or-email? username-or-email) username-or-email})

(defn lookup [username-or-email]
  (if-let [user (mc/find-one-as-map db users
                                    (lookup-cond username-or-email))]
    (update-in user [:roles]
               (comp set (partial map #(keyword "retroboard.user" %))))))

(defn email? [email]
  (and email (re-find  #".+@.+\..*" email)))

(defn valid-email? [email]
  (and (email? email)
       (not (mc/any? db users {:email email}))))

(defn valid-username? [username]
  (and (not (email? username))
       (not (mc/any? db users {:username username}))))

(defn valid-password? [password]
  (> (count password) 6))

(defn new-user [username email password]
  {:username username
   :email email
   :password (creds/hash-bcrypt password)
   :boards #{}
   :roles #{::user}
   :_id (ObjectId.)})

(defn add-user [username email password]
  (when (and (valid-username? username)
             (valid-email? email)
             (valid-password? password))
    (mc/insert-and-return db users (new-user username email password))))

(defn delete-user [username-or-email]
  (mc/remove db users (lookup-cond username-or-email)))

(defn cred-fn [creds]
  (creds/bcrypt-credential-fn lookup creds))

(defn add-board [username eid]
  (let [user (lookup username)]
    (mc/update-by-id db users
                     (:_id user)
                     (update-in user [:boards] (comp set #(conj % eid))))))

(defn boards [username]
  (:boards (lookup username)))
