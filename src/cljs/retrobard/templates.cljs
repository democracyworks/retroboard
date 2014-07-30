(ns retroboards.templates
  (:require [retroboard.actions :as a]
            [retroboard.resource :refer [temprid]]))

(def rid temprid)

(def retrospective
  [(a/new-column (rid 0) "The Good")
   (a/new-column (rid 1) "The Bad")
   (a/new-column (rid 2) "The Unknown")
   (a/new-column (rid 3) "Action Items")])

(def pros-and-cons
  [(a/new-column (rid 0) "Pros")
   (a/new-column (rid 1) "Cons")])
