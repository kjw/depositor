(ns depositor.auth
  (:require [depositor.path :refer [path-to]]
            [org.httpkit.client :as hc]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [compojure.core :refer [GET ANY defroutes]]
            [cemerick.friend :refer [logout]]
            [ring.util.response :refer [redirect]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [hiccup.core :refer [html]]
            [hiccup.form :refer [hidden-field]]))

;(def ^:dynamic *anti-forgery-token* nil)

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
  (let [params {:rtype "prefixes" :usr username :pwd password}
        request @(hc/get "https://doi.crossref.org/info" {:query-params params})]
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
  (logout (ANY "/logout" [] (redirect (path-to "login")))))


