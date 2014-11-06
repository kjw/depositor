(ns depositor.deposit
  (:require [hiccup.core :refer [html]]
            [compojure.core :refer [defroutes GET]]
            [org.httpkit.client :as hc]
            [clojure.data.json :as json]
            [depositor.event :refer [handle-socket-event]]
            [depositor.layout :refer [identity-credentials page-with-sidebar]]))

(defn get-deposits [{:keys [username password]} params]
  (-> "https://api.crossref.org/v1/deposits"
      (hc/get {:basic-auth [username password]
               :query-params params})
      deref
      :body
      (json/read-str :key-fn keyword)
      :message))

(defn get-deposit [{:keys [username password]} id]
  (-> (str "https://api.crossref.org/v1/deposits/" id)
      (hc/get {:basic-auth [username password]})
      deref
      :body
      (json/read-str :key-fn keyword)
      :message))

(defn put-deposit [{:keys [username password]}
                   {:keys [url filename content-type]}]
  (let [{:keys [headers status body error]}
        (-> "https://api.crossref.org/v1/deposits"
            (hc/post {:basic-auth [username password]
                      :query-params {:url url
                                     :filename filename}
                      :headers {"Content-Type" content-type}})
            deref)]
    (-> body (json/read-str :key-fn keyword) :message)))

(defn deposits-page [t]
  [:div
   [:div#deposits-page
    [:h4 "Deposits"]
    [:div#deposits]]
   [:div#deposit-page.hidden
    [:h4 "Deposit"]
    [:div#deposit]]])

   ;[(keyword (str "div#" (name t) "-deposits"))]])

;; (defn handle-socket-event ::deposit
;;   [{:keys [ring-req ?reply-fn ?data]}]
  
(defmethod handle-socket-event ::deposits
  [{:keys [ring-req ?reply-fn ?data]}]
  (-> ring-req identity-credentials (get-deposits ?data) ?reply-fn))

(defmethod handle-socket-event ::deposit
  [{:keys [ring-req ?reply-fn ?data]}]
  (when-let [did (:id ?data)]
    (-> ring-req identity-credentials (get-deposit did) ?reply-fn)))

(defmethod handle-socket-event ::deposit-link
  [{:keys [ring-req ?reply-fn ?data]}]
  (-> ring-req identity-credentials (put-deposit ?data) ?reply-fn))
    
(defroutes deposit-routes
  (GET "/finished" req (page-with-sidebar req (deposits-page :finished)))
  (GET "/failed" req (page-with-sidebar req (deposits-page :failed)))
  (GET "/incomplete" req (page-with-sidebar req (deposits-page :incomplete))))






