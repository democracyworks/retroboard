(ns retroboard.environment
  (:require [taoensso.carmine :as car :refer [wcar]]
            [retroboard.util :refer [rand-str]]
            [retroboard.resource :as resource]
            [clojure.edn :as edn]))

(def redis-uri (or (System/getenv "REDISTOGO_URL") "redis://127.0.0.1:6379"))

(def server1-conn {:pool {} :spec {:uri redis-uri}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(defn create []
  (let [eid (rand-str 5)
        is-new (= (wcar* (car/setnx eid (pr-str []))) 1)]
    (if is-new eid (recur))))

(defn exists? [eid]
  (wcar* (car/get eid)))

(defn resource-generator [eid]
  (let [rid (str eid "." "current-rid")]
    (fn []
      (wcar* (car/incr rid)))))


(defn clj-append [key coll]
  (car/lua "return redis.call('set', _:key,
                                    string.sub(redis.call('get', _:key),0,-2)
                                    ..' '
                                    ..string.sub(_:vec, 2,-1))"
           {:key key}
           {:vec (-> coll vec pr-str)}))

(defn append-actions [eid actions]
  (let [actions-with-ids (resource/replace-ids (resource-generator eid)
                                               actions)]
    (wcar* (clj-append eid actions-with-ids))
    actions-with-ids))

(defn history [eid]
  (edn/read-string (wcar* (car/get eid))))
