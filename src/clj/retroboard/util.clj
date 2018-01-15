(ns retroboard.util
  (:require clojure.edn))

(def valid-chars
  (map char (concat (range 48 58)
                    (range 97 123))))

(defn rand-char []
  (rand-nth valid-chars))

(defn rand-str [len]
  (apply str (take len (repeatedly rand-char))))

(defn edn-resp [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})


;;; EDN middleware
(defn- edn-request?
  [req]
  (if-let [^String type (:content-type req)]
    (not (empty? (re-find #"^application/(vnd.+)?edn" type)))))

(defprotocol EdnRead
  (-read-edn [this]))

(extend-type String
  EdnRead
  (-read-edn [s]
    (clojure.edn/read-string {:readers *data-readers*} s)))

(extend-type java.io.InputStream
  EdnRead
  (-read-edn [is]
    (clojure.edn/read
     {:eof nil :readers *data-readers*}
     (java.io.PushbackReader.
                       (java.io.InputStreamReader.
                        is "UTF-8")))))

(defn wrap-edn-params
  [handler]
  (fn [req]
    (if-let [body (and (edn-request? req) (:body req))]
      (let [edn-params (binding [*read-eval* false] (-read-edn body))
            req* (assoc req
                   :edn-params edn-params
                   :params (merge (:params req) edn-params))]
        (handler req*))
      (handler req))))

(defn mongo-uri []
  (or (System/getenv "MONGO_URI")
      "mongodb://localhost/retroboard"))

(defn redis-uri []
  (or (System/getenv "REDIS_URI")
      "redis://localhost:6379"))
