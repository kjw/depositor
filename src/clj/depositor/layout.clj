(ns depositor.layout
  "Define various bits of a page layout."
  (:require [hiccup.core :refer [html]]
            [depositor.page :refer [page]]))

(defn authentication [req]
  (let [identity (get-in req [:session 
                              :cemerick.friend/identity
                              :current])]
    (get-in req [:session 
                 :cemerick.friend/identity 
                 :authentications 
                 identity])))

(defn identity-name 
  "Return a  name for the logged in identity."
  [req & {:keys [friendly] :or {friendly true}}]
  (let [auth (authentication req)]
    (if (and friendly (-> auth :members count (= 1)))
      (-> auth :members first :name) (:identity auth))))

(defn identity-members [req]
  (-> (authentication req) :members))

(defn identity-roles [req]
  (-> (authentication req) :roles))

(defn identity-credentials [req]
  (let [{:keys [identity deposit-password]} (authentication req)]
    {:username identity :password deposit-password}))

(defn identity-prefixes [req]
  (filter (partial instance? String) (identity-roles req)))

(defn- header-links [req]
  [:div
   [:div#upload]
   [:ul.nav.navbar-nav]])

(defn- header-account-info [req]
  [:ul.nav.navbar-nav.navbar-right
   [:li [:a {:href "#"
             :data-target "#upload-modal"
             :data-toggle "modal"}
         [:span.glyphicon.glyphicon-upload] " Upload"]]
   [:li [:a {:href "#"} [:span.glyphicon.glyphicon-list] " Notifications"]]
   [:li.dropdown
    [:a.dropdown-toggle 
     {:href "#" :data-toggle "dropdown"}
     (identity-name req :friendly true)
     "&nbsp;"
     [:span.small.glyphicon.glyphicon-chevron-down]]
    [:ul.dropdown-menu
     [:li [:a {:href "/logout"} "Sign out"]]]]])

(defn- header [req]
  [:div.navbar.navbar-inverse {:role "navigation"}
   [:div.container
    [:div.navbar-header
     [:button.navbar-toggle 
      {:type "button" :data-toggle "collapse" :data-target ".navbar-collapse"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar]
      [:span.icon-bar]
      [:span.icon-bar]]
     [:a.navbar-brand {:href "#"}
      [:span
       [:img {:src "/img/logo.png" :style "width:160px;margin-top:-6px;"}]
       [:span {:style "font-size: .9em;"} "&nbsp;depositor"]]]]
    [:div.collapse.navbar-collapse
     (header-links req)
     (header-account-info req)]]])

(defn- sidebar [req]
  [:div#sidebar
   [:div.sidebar-block
    [:h4 "Deposits"]
    [:ul.list-unstyled
     [:li [:a {:href "/deposits/all"}
           [:span.glyphicon.glyphicon-th.link-icon] "All"]]
     [:li [:a {:href "/deposits/incomplete"}
           [:span.glyphicon.glyphicon-repeat.link-icon] "In progress"]]
     [:li [:a {:href "/deposits/finished"}
           [:span.glyphicon.glyphicon-ok-sign.link-icon] "Finished"]]
     [:li [:a {:href "/deposits/failed"}
           [:span.glyphicon.glyphicon-warning-sign.link-icon] "Failed"]]]]
   [:div.sidebar-block
    [:h4 "Member"]
    [:ul.list-unstyled
     [:li [:a {:href "/statistics"} 
           [:span.glyphicon.glyphicon-stats.link-icon] "Statistics"]]]]
   [:div.sidebar-block
    [:h4 "Account"]
    [:p.small "Signed in as " [:b (identity-name req :friendly false)]]
    [:ul.list-unstyled
     [:li [:a {:href "/permissions"}
           [:span.glyphicon.glyphicon-tower.link-icon] "Permissions"]]]]])

(defn- sidebar-layout [req contents]
  [:div.container
   [:div.row
    [:div.col-xs-4.col-md-3 (sidebar req)]
    [:div.col-xs-14.col-md-9 contents]]])

(defn page-with-frame [req & contents]
  (page :content (html [:div#page (header req) contents])))

(defn page-with-sidebar [req & contents]
  (page :content (html [:div#page 
                        (header req) 
                        (sidebar-layout req contents)])))

(defn page-without-frame [req & contents]
  (page :content (html [:div#page contents])))
        
  





