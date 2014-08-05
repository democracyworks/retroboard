(ns retroboard.actions
  (:require-macros [retroboard.macros :refer [defactions]]))

(defactions apply-action
  (new-column
   [id header state]
   (assoc state id {:header header :notes {}}))

  (delete-column
   [id state]
   (dissoc state id))

  (edit-column-header
   [id new-header state]
   (assoc-in state [id :header] new-header))

  (new-note
   [id column-id text state]
   (assoc-in state [column-id :notes id] {:text text :votes #{}}))

  (delete-note
   [column-id id state]
   (update-in state [column-id :notes] dissoc id))

  (new-vote
   [id column-id note-id state]
   (update-in state [column-id :notes note-id :votes] conj id))

  (edit-note
   [id column-id new-text state]
   (assoc-in state [column-id :notes id :text] new-text))

  (move-note
   [id column-id new-column-id state]
   (let [note (get-in state [column-id :notes id])]
     (-> state
         (update-in [column-id :notes] dissoc id)
         (assoc-in [new-column-id :notes id] note)))))
