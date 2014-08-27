(ns retroboard.templates
  (:require [retroboard.actions :as a]
            [retroboard.resource :refer [temprid]]))

(def rid temprid)

(def retro
  [(a/new-column (rid 0) "The Good")
   (a/new-column (rid 1) "The Bad")
   (a/new-column (rid 2) "The Unknown")
   (a/new-column (rid 3) "Action Items")])

(def pros-and-cons
  [(a/new-column (rid 0) "Pros")
   (a/new-column (rid 1) "Cons")])

(def card-wall
  [(a/new-column (rid 0) "ready to start")
   (a/new-column (rid 1) "in progress")
   (a/new-column (rid 2) "ready for review")
   (a/new-column (rid 3) "accepted")])
