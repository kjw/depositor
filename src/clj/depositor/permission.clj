(ns depositor.permission
  (:require [hiccup.core :refer [html]]
            [org.httpkit.client :as hc]
            [clojure.data.json :as json]
            [compojure.core :refer [defroutes GET]]
            [depositor.layout :refer [page-with-sidebar identity-members
                                      identity-name identity-prefixes]]))

(defn get-doi-count [path]
  (future
    (let [request (-> (str "http://api.crossref.org/v1/" path)
                      (hc/get {:query-params {:rows 0}})
                      deref)]
      (when (-> request :status (= 200))
        (format 
         "%,d"
         (-> request
             :body
             (json/read-str :key-fn keyword)
             :message
             :total-results))))))

(defn get-prefix-doi-count [prefix] 
  (get-doi-count (str "prefixes/" prefix "/works")))

(defn get-member-doi-count [id] 
  (get-doi-count (str "members/" id "/works")))

(defn permission-page [req]
  (let [prefixes (reduce 
                  #(conj %1 {:prefix %2 :count (get-prefix-doi-count %2)})
                  []
                  (identity-prefixes req))
        members (reduce
                 #(conj %1 (assoc %2 :count (get-member-doi-count (:id %2))))
                 []
                 (identity-members req))]
    [:div#permissions
     [:div.page-title
      [:h4 "Permissions"]]
     [:div.panel.panel-default
      [:div.panel-body
       [:h4.panel-title "Prefixes"] 
       [:p 
        "The account "
        [:b [:i (identity-name req :friendly false)]]
        " may deposit under these prefixes:"]
       [:table.table
        [:thead
         [:tr [:th "Prefix"] [:th "Registered DOIs"]]]
        [:tbody
         (for [prefix prefixes]
           [:tr [:td (:prefix prefix)] [:td @(:count prefix)]])]]
       [:p "This account  may also be able to " [:b [:i "deposit updates"]] 
        " (though not new content) for DOIs of other prefixes if you"
        " have received a transfer of content."]
       [:a "Request a prefix or journal transfer"]]]
     [:div.panel.panel-default
      [:div.panel-body
       [:h4.panel-title "Organisations"]
       [:p "This account is related to the following organisations:"]
       [:table.table
        [:thead
         [:tr [:th "ID"] [:th "Name"] [:th "Registered DOIs"]]]
        [:tbody
         (for [member members]
           [:tr 
            [:td [:a 
                  {:href (str "https://api.crossref.org/v1/members/" (:id member))}
                  (:id member)]] 
            [:td (:name member)]
            [:td @(:count member)]])]]]]]))

(defroutes permission-routes
  (GET "/" req (page-with-sidebar req (permission-page req))))

















