(ns depositor.auth
  (:require [org.httpkit.client :as hc]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [compojure.core :refer [GET ANY defroutes]]
            [cemerick.friend :refer [logout]]
            [ring.util.response :refer [redirect]]
            [hiccup.core :refer [html]]
            [hiccup.form :refer [hidden-field]]))

(def ^:dynamic *anti-forgery-token* nil)

(defn- login-page [req]
  (html
   [:h2 "Login"]
   (when (get-in req [:params :login_failed])
     [:p "Credentials do not match."])
   [:form {:action "/login" :method "POST"}
    (hidden-field 
     "__anti-forgery-token"
     (get-in req [:session 
                  :ring.middleware.anti-forgery/anti-forgery-token]))
    [:input {:type "text" :name "username" 
             :value (get-in req [:params :username])}]
    [:input {:type "text" :name "password"}]
    [:input {:type "submit" :name "submit" :value "submit"}]]))

;; todo rows, repeat on fail
(defn- prefixes->members 
  "Given a list of owner prefixes, return all related member ID, name pairs."
  [prefixes]
  (let [member-filter (->> prefixes 
                           (map #(str "prefix:" %))
                           (string/join ","))
        params {:filter member-filter :rows 10000}
        request @(hc/get "http://api.crossref.org/v1/members" 
                         {:query-params params})]
    (when (-> request :status (= 200))
      (let [members (-> request
                        :body
                        (json/read-str :key-fn keyword)
                        (get-in [:message :items]))]
        (map #(hash-map :id (:id %) :name (:primary-name %)) members)))))

(defn crossref-credentials [{:keys [username password]}]
  (let [pid (str username ":" password)
        params {:rtype "prefixes" :pid pid}
        request @(hc/get "http://doi.crossref.org/info" {:query-params params})]
    (when (-> request :status (= 200))
      (let [prefixes (-> request 
                         :body 
                         (json/read-str :key-fn keyword) 
                         :allowed-prefixes)]
        {:identity username
         :deposit-password password
         :members (prefixes->members prefixes)
         :roles (set (conj prefixes :user))}))))

(defroutes authorization-routes
  (GET "/login" req (login-page req))
  (logout (ANY "/logout" [] (redirect "/"))))

