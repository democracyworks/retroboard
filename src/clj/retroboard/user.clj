(ns retroboard.user
  (:require [cemerick.friend.credentials :as creds]
            [taoensso.carmine :as car :refer [wcar]]))

(def redis-uri (or (System/getenv "REDISCLOUD_URL") "redis://127.0.0.1:6379"))

(def server1-conn {:pool {} :spec {:uri redis-uri}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))
(def user-map "users")


(defn email->user [email]
  (wcar* (car/hget user-map email)))

(defn new-user [email password name]
  {:username email
   :password (creds/hash-bcrypt password)
   :email email
   :name name
   :boards #{}
   :roles #{::user}})

(defn add-user [email password name]
  (wcar* (car/hsetnx user-map email (new-user email password name))))

(defn cred-fn [creds]
  (creds/bcrypt-credential-fn email->user creds))

(defn add-board [email eid]
  (let [user (email->user email)]
    (wcar* (car/hset user-map email
                     (update-in user [:boards] conj eid)))))

(defn boards [email]
  (:boards (email->user email)))
