(ns retroboard.actions
  (:require-macros [retroboard.macros :refer [defactions]]))

(defactions apply-action
  (edit-name
   [new-name state]
   (assoc state :name new-name))

  (user-join
   [state]
   (update-in state [:user-count] inc))

  (user-leave
   [state]
   (update-in state [:user-count] dec))

  (new-column
   [id header state]
   (assoc-in state [:columns id] {:header header :notes {}}))

  (delete-column
   [id state]
   (update-in state [:columns] dissoc id))

  (edit-column-header
   [id new-header state]
   (assoc-in state [:columns id :header] new-header))

  (new-note
   [id column-id text state]
   (assoc-in state [:columns column-id :notes id] {:text text :votes #{}}))

  (delete-note
   [column-id id state]
   (update-in state [:columns column-id :notes] dissoc id))

  (new-vote
   [id column-id note-id state]
   (update-in state [:columns column-id :notes note-id :votes] conj id))

  (edit-note
   [id column-id new-text state]
   (assoc-in state [:columns column-id :notes id :text] new-text))

  (move-note
   [id column-id new-column-id state]
   (if-let [note (get-in state [:columns column-id :notes id])]
     (-> state
         (update-in [:columns column-id :notes] dissoc id)
         (assoc-in [:columns new-column-id :notes id] note))
     state)))

(def new-board {:name "[Title]"})
