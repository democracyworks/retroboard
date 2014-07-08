(ns retroboard.macros)

(defmacro defaction [mult-name [name action-args & body]]
  (let [args (drop-last action-args)
        state (last action-args)]
    `(do (defn ~name [connection# ~@args]
           (cljs.core.async/put!
            (:to-send connection#)
            {:cmd :action
             :action [(keyword '~name)
                      ~(into {} (map (fn [s#] [(keyword s#) s#])
                                     args))]}))
         (defmethod ~mult-name (keyword '~name) [[type# action#]]
           (let [{:keys ~args} action#]
             (fn [~state] ~@body))))))


(defmacro defactions [name & actions]
  `(do (defmulti ~name first)
       ~@(map (fn [action#] `(defaction ~name ~action#)) actions)))
