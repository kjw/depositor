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
 
(defn paginate [total rows offset]
  (let [current-page (if (zero? offset)
                       1
                       (inc (/ total offset)))]
    (dom/div
     {:class "row"}
     (dom/div
      {:class "col-md-8"}
      (dom/ul
       {:class "pagination"}
       (dom/li (dom/a {:href "#"} "&laquo;"))
       (for [pg (map inc (range (/ total rows)))]
         (if (= pg current-page)
           (dom/li {:class "active"} (dom/a {:href "#"} pg))
           (dom/li (dom/a {:href "#"} pg))))
       (dom/li (dom/a {:href "#"} "&raquo;"))))
     (dom/div
      {:class "col-md-4 text-right-align"}
      (dom/span
       {:class "small"}
       (str "Page " current-page " of " total " results"))))))

(defn paginate-list
  "Paginate a REST API list message"
  [msg]
  (paginate
   (:total-results msg)
   (:items-per-page msg)
   (get-in msg [:query :start-index])))

(defn format-percent [n]
  (str (->> n (* 100) (gs/format "%1.1f")) "%"))

(defn format-integer [n]
  (gs/format "%d" n))

(defn friendly-date [n]
  (let [fmt (tf/formatter "dth MMMM, HH:mm")]
    (tf/unparse fmt (tc/from-long n))))










