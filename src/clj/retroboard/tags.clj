(ns retroboard.tags
  (:require [retroboard.resource :as resource]
            [cljs.tagged-literals :as tags]))

(alter-var-root #'tags/*cljs-data-readers* assoc 'r/id resource/read-rid)
