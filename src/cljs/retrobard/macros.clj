(ns retroboard.macros)

(defmacro defaction [name action-args state & body]
  `(do (if-not ~'apply-action (defmulti ~'apply-action first))
       (defn ~name [connection# ~@action-args]
         (cljs.core.async/put!
          (:to-send connection#)
          {:cmd :action
           :action [(keyword '~name)
                    ~(into {} (map (fn [s#] [(keyword s#) s#])
                                   action-args))]}))
       (defmethod ~'apply-action (keyword '~name) [[type# action#]]
         (let [{:keys ~action-args} action#]
           (fn [~state] ~@body)))))
