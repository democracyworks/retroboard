(ns retroboard.environment
  (:require [taoensso.carmine :as car :refer [wcar]]
            [retroboard.util :refer [rand-str]]
            [clojure.core.async :refer [chan put!]]
            [retroboard.resource :as resource]
            [clojure.edn :as edn]))

(def redis-uri (or (System/getenv "REDISCLOUD_URL") "redis://127.0.0.1:6379"))

(def server1-conn {:pool {} :spec {:uri redis-uri}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))
(def env-map "envs")

(defn create []
  (let [eid (rand-str 5)
        is-new (= (wcar* (car/hsetnx env-map eid (pr-str []))) 1)]
    (if is-new eid (recur))))

(defn exists? [eid]
  (= (wcar* (car/hexists env-map eid)) 1))

(def channel-id (partial str "env."))

(defn publish-actions [eid actions]
  (wcar* (car/publish (channel-id eid) actions)))

(def listener
  (car/with-new-pubsub-listener (:spec server1-conn)
    {}))

(defn subscribe [eid]
  (let [ch (chan 3)]
    (car/with-open-listener listener
      (car/subscribe (channel-id eid)))
    (swap! (:state listener) assoc (channel-id eid)
           (fn [[type chname message]]
             (if (= type "message")
               (put! ch message))))
    ch))

(defn resource-generator [eid]
  (let [rid (str eid "." "current-rid")]
    (fn []
      (wcar* (car/incr rid)))))


(defn clj-append [key coll]
  (car/lua "return redis.call('hset', _:map, _:key,
                                    string.sub(redis.call('hget',_:map, _:key),0,-2)
                                    ..' '
                                    ..string.sub(_:vec, 2,-1))"
           {:map env-map :key key}
           {:vec (-> coll vec pr-str)}))

(defn append-actions [eid actions]
  (let [actions-with-ids (resource/replace-ids (resource-generator eid)
                                               actions)]
    (wcar* (clj-append eid actions-with-ids))
    actions-with-ids))

(defn history [eid]
  (edn/read-string (wcar* (car/hget env-map eid))))
