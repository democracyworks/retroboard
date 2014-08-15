(ns retroboard.util)

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn translate3d [doit? x y]
  (if doit?
    #js {:transform (str "translate3d(" x "px, " y "px,0)")}
    #js {}))
