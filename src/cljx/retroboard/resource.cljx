(ns retroboard.resource
  (:require [clojure.walk :refer [postwalk]]
            #+cljs [cljs.reader :as reader]))

(defrecord TempResourceId [index])

(defn temprid
  ([] (temprid 0))
  ([idx] (TempResourceId. idx)))

(defn to-str [rid]
  (str "#r/id " (:index rid)))

(defn read-rid [index]
  (TempResourceId. index))

#+clj
(defmethod clojure.core/print-method TempResourceId [rid writer]
  (.write writer (to-str rid)))

(def ^:dynamic *readers*
  {'r/id read-rid})

#+cljs
(defn register-tag-parsers! []
  (doseq [[tag f] *readers*]
    (reader/register-tag-parser! tag f)))

#+cljs
(register-tag-parsers!)

#+cljs
(extend-protocol IPrintWithWriter
  TempResourceId
  (-pr-writer [rid writer opts]
    (-write writer (to-str rid))))


(defn replace-ids [id-factory data]
  (let [mapped-ids (atom {})]
    (postwalk (fn [form]
                (if (= TempResourceId (type form))
                  (if-let [existing-id (@mapped-ids form)]
                    existing-id
                    (let [new-id (id-factory)]
                      (swap! mapped-ids assoc form new-id)
                      new-id))
                  form))
              data)))
