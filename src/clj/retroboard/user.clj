(ns retroboard.user
  (:require [cemerick.friend.credentials :as creds]
            [monger.core :as mg]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

(def mongo-uri (or (System/getenv "MONGOHQ_URL") "mongodb://127.0.0.1/retroboard"))

(def db (:db (mg/connect-via-uri mongo-uri)))
(def users "users")

(defn lookup [email]
  (if-let [user (mc/find-one-as-map db users
                                    {:email email})]
    (update-in user [:roles]
               (comp set (partial map #(keyword "retroboard.user" %))))))

(defn email? [email]
  (and email (re-find  #".+@.+\..*" email)))

(defn valid-email? [email]
  (and (email? email)
       (not (mc/any? db users {:email email}))))

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
    (mc/insert-and-return db users (new-user email password))))

(defn delete-user [email]
  (mc/remove db users {:email email}))

(defn cred-fn [creds]
  (creds/bcrypt-credential-fn lookup creds))

(defn add-board [email eid]
  (let [user (lookup email)]
    (mc/update-by-id db users
                     (:_id user)
                     (update-in user [:boards] (comp set #(conj % eid))))))

(defn boards [email]
  (:boards (lookup email)))
