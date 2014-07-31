(ns retroboard.actions
  (:require-macros [retroboard.macros :refer [defactions]]))

(defactions apply-action
  (new-column [id header state]
              (assoc state id {:header header :notes {}}))
  (delete-column [id state]
                 state (dissoc state id))
  (new-note [id column-id text state]
            (assoc-in state [column-id :notes id] {:text text :votes #{}}))
  (delete-note [column-id id state]
               (update-in state [column-id :notes] dissoc id))
  (new-vote [id column-id note-id state]
            (update-in state [column-id :notes note-id :votes] conj id))
  (edit-note [id column-id new-text state]
             (assoc-in state [column-id :notes id :text] new-text)))
