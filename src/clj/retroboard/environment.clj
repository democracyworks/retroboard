(ns retroboard.environment
  (:require [taoensso.carmine :as car :refer [wcar]]
            [retroboard.util :refer [rand-str]]
            [clojure.core.async :refer [chan put! mult tap untap]]
            [retroboard.resource :as resource]
            [clojure.edn :as edn]))

(defn redis-uri [] (if (System/getenv "REDIS_PORT_6379_TCP_ADDR")
                     (str "redis:"
                          (System/getenv "REDIS_PORT_6379_TCP_ADDR")
                          ":"
                          (System/getenv "REDIS_PORT_6379_TCP_PORT"))
                     "redis://127.0.0.1:6379"))

(defn server1-conn [] {:pool {} :spec {:uri (redis-uri)}})
(defmacro wcar* [& body] `(car/wcar (server1-conn) ~@body))
(def env-map "envs")

(defn find-id []
  (let [eid (rand-str 5)
        new? (= (wcar* (car/hsetnx env-map eid (pr-str []))) 1)]
    (if new? eid (recur))))

(defn create [on-join on-leave]
  (let [eid (find-id)]
    (if (and on-join on-leave)
      (wcar* (car/set (str env-map "." eid ".on-join") on-join)
             (car/set (str env-map "." eid ".on-leave") on-leave)))
    eid))

(defn delete [eid]
  (= (wcar* (car/hdel env-map eid)) 1))

(defn exists? [eid]
  (= (wcar* (car/hexists env-map eid)) 1))

(def channel-id (partial str "env."))

(defn publish-actions [eid actions]
  (wcar* (car/publish (channel-id eid) actions)))

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
    (publish-actions eid actions-with-ids)
    actions-with-ids))

(defn on-join [eid]
  (append-actions eid [(wcar* (car/get (str env-map "." eid ".on-join")))]))

(defn on-leave [eid]
  (append-actions eid [(wcar* (car/get (str env-map "." eid ".on-leave")))]))

(defn history [eid]
  (edn/read-string (wcar* (car/hget env-map eid))))


(let [listener (atom nil)]
  (defn get-listener []
    (swap! listener
           (fn [l] (or l (car/with-new-pubsub-listener (:spec server1-conn) {}))))
    @listener))

(defn subscribe-to-redis [eid ch]
  (car/with-open-listener (get-listener)
    (car/subscribe (channel-id eid)))
  (swap! (:state (get-listener)) assoc (channel-id eid)
         (fn [[type _ message]]
           (if (= type "message")
             (put! ch message)))))

(defn unsubscribe-from-redis [eid]
  (car/with-open-listener (get-listener)
    (car/unsubscribe (channel-id eid)))
  (swap! (:state (get-listener)) dissoc (channel-id eid)))

(defn add-subscriber [eid subscribers]
  (let [[_ subscribe-chan]
        (swap! subscribers
               (fn [[subscribers]]
                 (let [ch (chan 3)]
                   (if (subscribers eid)
                     [(update-in subscribers [eid :count] inc)]
                     [(assoc subscribers eid
                             {:mult (mult ch)
                              :count 1})
                      ch]))))]
    (when subscribe-chan
      (subscribe-to-redis eid subscribe-chan))))

(defn remove-subscriber [eid subscribers]
  (let [[_ unsubscribe?]
        (swap! subscribers
               (fn [[subscribers]]
                 (if (= (get-in subscribers [eid :count]) 1)
                   [(dissoc subscribers eid) true]
                   [(update-in subscribers [eid :count] dec)])))]
    (when unsubscribe?
      (unsubscribe-from-redis eid))))


(let [subscribers (atom [{}])
      get-mult (fn [eid] (get-in @subscribers [0 eid :mult]))]
  (defn subscribe [eid subscriber-chan]
    (add-subscriber eid subscribers)
    (tap (get-mult eid) subscriber-chan))

  (defn unsubscribe [eid subscriber-chan]
    (untap (get-mult eid) subscriber-chan)
    (remove-subscriber eid subscribers)))
