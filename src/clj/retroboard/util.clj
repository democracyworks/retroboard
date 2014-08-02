(ns retroboard.util)

(def valid-chars
  (map char (concat (range 48 58)
                    (range 97 123))))

(defn rand-char []
  (rand-nth valid-chars))

(defn rand-str [len]
  (apply str (take len (repeatedly rand-char))))
