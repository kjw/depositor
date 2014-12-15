(ns depositor.notify
  (:require [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om.core :as om]
            [clojure.string :refer [capitalize]]))

(def messages-state (atom []))

(defn add-message [type]
  (-> (swap! messages-state conj {:type type :finished false})
      count
      dec))

(defn finish-message [id]
  (swap!
   messages-state
   #(let [msg-type (get-in % [id :type])
          new-messages-state (assoc-in % [id :finished] true)]
      (if (zero? (->> new-messages-state
                      (filter (complement :finished))
                      (filter (fn [e] (= (:type e) msg-type)))
                      count))

        ;; all of that type finished - mark all :dead
        (vec
         (map (fn [e] (if (= (:type e) msg-type)
                       (assoc e :dead true)
                       e))
              new-messages-state))

        ;; some unfinished - mark this one finished
        (vec new-messages-state)))))

(defn message-type-label [type] (-> type name capitalize))

(defn messages-type-view [type messages]
  (let [finished-count (->> messages (filter :finished) count inc)
        total-count (count messages)]
    (dom/div
     (dom/h5
      (str (message-type-label type)
           " " finished-count " of " total-count "..."))
     (dom/div
      {:class "progress progress-striped active"}
      (dom/div {:class "progress-bar"
                :aria-valuenow "100"
                :aria-valuemin "0"
                :aria-valuemax "100"
                :style {:width "100%"}})))))

(defn messages-view [grouped-messages]
  (dom/div
   {:class "container"}
   (dom/div
    {:class "row"}
    (dom/div
     {:class "col-md-3 fadein fadeout"
      :style {:border-color "#dddddd" :border-top-left-radius ".5em"
              :border-top "1px solid" :border-left "1px solid"
              :border-right "1px solid" :z-index "999999"
              :background-color "#FFFFFF" :position "fixed"
              :bottom "0px" :border-top-right-radius ".5em"}}
     (for [[type messages] grouped-messages]
       (messages-type-view type messages))))))

(defcomponent messages [messages-state owner]
  (render-state [_ _]
                (->> messages-state
                     (filter (complement :dead))
                     (group-by :type)
                     messages-view)))

(when-let [e (.getElementById js/document "notify")]
  (om/root messages messages-state {:target e}))

