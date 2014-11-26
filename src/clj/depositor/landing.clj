(ns depositor.landing
  (:require [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [redirect]]
            [depositor.layout :refer [identity-name page-without-frame]]))

(defn landing-page []
  [:div.row
   [:div.col-xs-12.col-md-5.col-md-offset-3
    [:img {:src "img/logo.png" :style "width:20em; margin-top: 5em"}]
    [:h3 {:style "margin-top: .3em;"} "depositor"]
    [:p.lead.muted {:style "margin-top: 3em;"} "Deposit scholarly content with CrossRef"]
    [:a.btn.btn-default {:href "/deposits/all"}
     [:span.glyphicon.glyphicon-log-in]
     " Sign in"]]])

(defroutes landing-routes
  (GET "/" req (if (identity-name req)
                 (redirect "/deposits/all")
                 (page-without-frame req (landing-page)))))

















