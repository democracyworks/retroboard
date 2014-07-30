(ns retroboard.macros)

(defn defaction [mult-name [name action-args & body]]
  (let [args (drop-last action-args)
        state (last action-args)
        data [(keyword name) (into {} (map (fn [s] [(keyword s) s]) args))]]
    `((defn ~name
        ([~@args]
           ~data)
        ([connection# ~@args]
           (cljs.core.async/put!
            (:to-send connection#)
            {:cmd :action
             :action ~data})))
      (defmethod ~mult-name (keyword '~name) [[type# action#]]
           (let [{:keys ~args} action#]
             (fn [~state] ~@body))))))


(defmacro defactions [name & actions]
  `(do (defmulti ~name first)
       ~@(apply concat (map (fn [action#] (defaction name action#)) actions))))
