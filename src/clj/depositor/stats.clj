(ns depositor.stats
  (:require [hiccup.core :refer [html]]
            [org.httpkit.client :as hc]
            [clojure.data.json :as json]
            [compojure.core :refer [GET defroutes]]
            [environ.core :refer [env]]
            [depositor.event :refer [handle-socket-event]]
            [depositor.layout :refer [page-with-sidebar identity-members]]))

(defn get-member-status [id]
  (let [response (-> (hc/get (str (env :api) "/v1/members/" id))
                     deref)]
    (when (-> response :status (= 200))
      (-> response
          :body
          (json/read-str :key-fn keyword)
          :message))))

(defn get-license-breakdown [id]
  (let [qp {:facet "license:10" :rows 0 :filter (str "member:" id)}
        response (-> (str (env :api) "/v1/works")
                     (hc/get {:query-params qp})
                     deref)]
    (when (-> response :status (= 200))
      (-> response
          :body
          (json/read-str)
          (get-in ["message" "facets" "license" "values"])
          vec
          ((partial sort-by second >))))))

(defn get-funding-breakdown [id]
  (let [qp {:facet "funder-name:10" :rows 0 :filter (str "member:" id)}
        response (-> (str (env :api) "/v1/works")
                     (hc/get {:query-params qp})
                     deref)]
    (when (-> response :status (= 200))
      (-> response
          :body
          (json/read-str)
          (get-in ["message" "facets" "funder-name" "values"])
          vec
          ((partial sort-by second >))))))

(defn get-license-uri-availability [uri]
  (let [{:keys [error status]} (-> uri hc/get deref)]
    (prn error)
    (and (not error) (= 200 status))))

(defn stats-page [req]
  [:div#statistics-page
   [:h4 "Metadata Statistics"]
   [:div#statistics]])

(defmethod handle-socket-event ::members
  [{:keys [ring-req ?reply-fn]}]
  (when ?reply-fn
    (-> ring-req identity-members ?reply-fn)))

(defn handle-member-id-event [ring-req ?reply-fn ?data collection-fn]
  (when ?reply-fn
    (if-let [member-id (:id ?data)]
      (-> member-id collection-fn ?reply-fn)
      (-> ring-req identity-members first :id collection-fn ?reply-fn))))

(defmethod handle-socket-event ::member-status
  [{:keys [ring-req ?reply-fn ?data]}]
  (handle-member-id-event ring-req ?reply-fn ?data get-member-status))

(defmethod handle-socket-event ::license-breakdown
  [{:keys [?data ring-req ?reply-fn]}]
  (handle-member-id-event ring-req ?reply-fn ?data get-license-breakdown))
  
(defmethod handle-socket-event ::funding-breakdown
  [{:keys [?data ring-req ?reply-fn]}]
  (handle-member-id-event ring-req ?reply-fn ?data get-funding-breakdown))

(defmethod handle-socket-event ::license-check
  [{:keys [?data ?reply-fn]}]
  (when ?reply-fn
    (-> ?data get-license-uri-availability ?reply-fn)))

(defroutes stats-routes
  (GET "/" req (page-with-sidebar req (stats-page req))))

