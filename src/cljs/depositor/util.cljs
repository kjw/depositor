(ns depositor.util
  (:require [om-tools.dom :as dom :include-macros true]
            [goog.string :as gs]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]))

(defn loader []
  (dom/div {:class "row"}
           (dom/div {:class "center-block" 
                     :style {:width "4em" 
                             :height "4em"}}
                    (dom/div {:class "spinner"}))))

(defn icon [k]
  (dom/span {:class (str "glyphicon glyphicon-" (name k))}))
 
(defn paginate [total rows offset & {:keys [change-fn]}]
  (let [current-page (inc (* (/ total rows) offset))]
    (dom/div
     {:class "row"}
     (dom/div
      {:class "col-md-8"}
      (dom/ul
       {:class "pagination"}
       (dom/li (dom/a {:href "#"} (icon :chevron-left)))
       (for [pg (map inc (range (/ total rows)))]
         (if (= pg current-page)
           (dom/li {:class "active"} (dom/a {:href "#"} pg))
           (dom/li
            (if change-fn
              (dom/a {:href "#" :on-click #(change-fn pg)} pg)
              (dom/a {:href "#"} pg)))))
       (dom/li (dom/a {:href "#"} (icon :chevron-right)))))
     (dom/div
      {:class "col-md-4 text-align-right"}
      (dom/span
       {:class "small"}
       (str "Page " current-page " of " total " results"))))))

(defn paginate-list
  "Paginate a REST API list message"
  [msg change-fn]
  (paginate
   (:total-results msg)
   (:items-per-page msg)
   (get-in msg [:query :start-index])
   :change-fn change-fn))

(defn tabs [ts]
  (dom/div
   (dom/ul
    {:class "nav nav-tabs" :role "tablist"}
    (for [t ts]
      (dom/li
       {:class (if (:active t) "active" "")
        :role "presentation"}
       (dom/a {:id (str (name (:name t)) "-tab")
               :data-toggle "tab"
               :role "tab"
               :href (str "#" (name (:name t)))}
              (:label t)))))
   (dom/div
    {:class "tab-content"}
    (for [t ts]
      (dom/div
       {:id (str (name (:name t)))
        :class (if (:active t)
                 "tab-pane fade in active"
                 "tab-pane fade")
        :role "tabpanel"}
       (:content t))))))

(defn in-panel [content & {:keys [title]}]
  (dom/div
   {:class "panel panel-default"}
   (dom/div
    {:class "panel-body"}
    (when title
      (dom/div {:class "panel-title"} title))
    content)))

(defn radios [name rs]
  (dom/div
   {:class "btn-group"
    :data-toggle "buttons"}
   (for [r rs]
     (dom/label
      {:class (if (:active r)
                "btn btn-primary btn-lg active"
                "btn btn-primary btn-lg")}
      (dom/input
       {:type "radio"
        :name (name (:name r))
        :id (name (:name r))
        :on-change (:change-fn r)
        :autocomplete "off"}
       (:label r))))))

(defn format-percent [n]
  (str (->> n (* 100) (gs/format "%1.1f")) "%"))

(defn format-integer [n]
  (gs/format "%d" n))

(defn friendly-date [n]
  (let [fmt (tf/formatter "dth MMMM, HH:mm")]
    (tf/unparse fmt (tc/from-long n))))










