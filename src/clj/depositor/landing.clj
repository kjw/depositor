(ns depositor.landing
  (:require [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :refer [redirect]]
            [hiccup.form :refer [hidden-field]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [depositor.path :refer [path-to img]]
            [depositor.layout :refer [identity-name page-without-frame]]))

(defn- login-failed? [req]
  (= "Y" (get-in req [:params :login_failed])))

(defn- landing-page [req]
  [:div
   [:div.row
    [:div.col-xs-12.col-md-5.col-md-offset-3
     [:img {:src (img "logo.png") :style "width:20em; margin-top: 5em;"}]
     [:h3 {:style "margin-top: .4em;"} "Linking Console " [:span.small "Beta"]]
     [:p.lead.muted {:style "margin-top: 2em;"} "Deposit metadata with CrossRef"]
     [:form {:action (path-to "login") :method "POST" :style "margin-top: 4em;"}
      [:h5 "Sign in"]
      (anti-forgery-field)
      [:div.form-group
       [:input.form-control
        {:style "width: 20em;" :type "text" :name "username"
         :placeholder "Username" :autofocus "autofocus"
         :value (get-in req [:params :username])}]]
      (if (login-failed? req)
        [:div.form-group.has-error
         [:input.form-control {:style "width: 20em;" :type "password"
                               :name "password" :placeholder "Password"}]]
        [:div.form-group
         [:input.form-control {:style "width: 20em;" :type "password"
                               :name "password" :placeholder "Password"}]])
      [:button.btn.btn-default
       [:span.glyphicon.glyphicon-log-in]
       " Sign in"]]]]
   [:div.row {:style "padding-top: 3em; padding-bottom: 3em; border-top: 3px solid 3FC073; background-color: #eee; margin-top: 4em;"}
    [:div.col-md-2.col-md-offset-3
     [:h4 "Citation Extraction"]
     [:p "Extract references from PDFs, link them to DOIs and deposit as citation lists."]]
    [:div.col-md-2
     [:h4 "Track Your Deposits"]
     [:p "Track manually uploaded XML and PDFs and find errors."]]
    [:div.col-md-2
     [:h4 "Metadata Statistics"]
     [:p "Statistics on the coverage and completeness of your CrossRef metadata."]]]])

(defroutes landing-routes
  (GET "/" req (redirect (path-to "login")))
  (POST "/login" req (if (identity-name req)
                         (redirect (path-to "deposits" "all"))
                         (redirect (path-to "login"))))
  (GET "/login" req (if (identity-name req)
                      (redirect (path-to "deposits" "all"))
                      (page-without-frame req (landing-page req)))))

















