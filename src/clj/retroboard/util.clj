(ns retroboard.util)

(def valid-chars
  (map char (concat (range 48 58)
                    (range 65 91)
                    (range 97 123))))

(defn rand-char []
  (rand-nth valid-chars))

(defn rand-str [len]
  (apply str (take len (repeatedly rand-char))))

(defn unique-str [len strs]
  (-> strs
      (remove (repeatedly #(rand-str len)))
      first))

(defn unique-str-generator [len]
  (let [used-strs (atom #{})]
    (fn []
      (let [new-str (unique-str len @used-strs)]
        (swap! used-strs conj new-str)
        new-str))))
