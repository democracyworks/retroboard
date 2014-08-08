(ns retroboard.xhr
  (:require [cljs.reader :as reader]
            [goog.events :as events]
            [cljs.core.async :refer [put!]])
  (:import goog.net.EventType
           goog.Uri.QueryData
           goog.net.XhrIoPool))

(defn ->query-string [params]
  (.toStringb
   (QueryData.createFromMap
    (->> params
         (remove (fn [[k v]] (or (nil? v) (= v ""))))
         (into {})
         clj->js))))

(def ^:private meths
  {:get "GET"
   :put "PUT"
   :post "POST"
   :delete "DELETE"
   :patch "PATCH"})

(defn parse-body [xhr]
  (try
    (reader/read-string (.getResponseText xhr))
    (catch js/Object e
      nil)))

(def xhr-pool (XhrIoPool.))

(defn edn-xhr [{:keys [method url data on-complete chan response-headers]}]
  (.getObject xhr-pool
              (fn [xhr]
                (this-as this
                         (events/listen xhr goog.net.EventType.COMPLETE
                                        (fn [e]
                                          (try
                                            (let [status (.getStatus xhr)
                                                  body (parse-body xhr)]
                                              (when chan (put! chan {:status status :body body}))
                                              (when on-complete
                                                (on-complete status
                                                             body)))
                                            (finally (.releaseObject this xhr))))))
                (. xhr
                   (send url (meths method) (when data (pr-str data))
                         #js {"Content-Type" "application/edn"})))))
