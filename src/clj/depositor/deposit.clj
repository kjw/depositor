(ns depositor.deposit
  (:require [clojure.string :as string]
            [hiccup.core :refer [html]]
            [compojure.core :refer [defroutes GET]]
            [org.httpkit.client :as hc]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [depositor.event :refer [handle-socket-event]]
            [depositor.generate :refer [citation-deposit]]
            [clj-time.format :as tf]
            [clj-time.coerce :as tco]
            [depositor.layout :refer [identity-credentials page-with-sidebar]]))

(def deposit-submit-time-format
  (tf/formatter "EEE MMM dd HH:mm:ss ZZZ yyyy"))

;; Below we choose an alternative +2GMT timezone to replace CEST,
;; which is not understood by clj-time.
(defn reformat-submit-time [deposit]
  (update-in
   deposit
   [:submitted-at]
   #(let [no-cest (string/replace % #"CEST" "EET")]
      (->> no-cest (tf/parse deposit-submit-time-format) tco/to-long))))

(defn prepare-filters [filterm]
  (->> filterm
       (filter (fn [[n vs]] (not (nil? vs))))
       (map (fn [[n vs]] (str (name n) ":" vs)))
       (string/join ",")))

(defn prepare-params [params]
  (if (:filter params)
    (update-in params [:filter] prepare-filters)
    params))

(defn get-deposits [{:keys [username password]} params]
  (update-in
      (-> "https://api.crossref.org/v1/deposits"
          (hc/get {:basic-auth [username password]
                   :query-params (prepare-params params)})
          deref
          :body
          (json/read-str :key-fn keyword)
          :message)
    [:items]
    #(map reformat-submit-time %)))

(defn get-deposit [{:keys [username password]} id]
  (-> (str "https://api.crossref.org/v1/deposits/" id)
      (hc/get {:basic-auth [username password]})
      deref
      :body
      (json/read-str :key-fn keyword)
      :message
      reformat-submit-time))

(defn put-deposit [{:keys [username password]}
                   {:keys [url filename content-type]}]
  (let [{:keys [headers status body error]}
        (-> "https://api.crossref.org/v1/deposits"
            (hc/post {:basic-auth [username password]
                      :query-params {:url url
                                     :filename filename}
                      :headers {"Content-Type" content-type}})
            deref)]
    (-> body
        (json/read-str :key-fn keyword)
        :message
        reformat-submit-time)))

(defn get-citation-matches [{:keys [text]}]
  (let [clean-text (string/replace text #"(?U)[^\w]+" " ")]
    (-> "http://api.crossref.org/v1/works"
        (hc/get {:query-params {:query clean-text :rows 4}})
        deref
        :body
        (json/read-str :key-fn keyword)
        :message
        :items)))

(defn generate-deposit [{:keys [username password]}
                        {:keys [from-id doi citations]}]
  (-> "https://api.crossref.org/v1/deposits"
      (hc/post {:basic-auth [username password]
                :query-params {:link from-id}
                :body (citation-deposit doi citations)})
      deref
      :body
      (json/read-str :key-fn keyword)
      :message
      reformat-submit-time))

(defn deposits-page [t]
  [:div {:style "margin-top: 30px"}
   [:div#deposits-page
    [:div#deposits]]])
  
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

(defmethod handle-socket-event ::citation-match
  [{:keys [?reply-fn ?data]}]
  (-> ?data get-citation-matches ?reply-fn))

(defmethod handle-socket-event ::generate-deposit
  [{:keys [ring-req ?reply-fn ?data]}]
  (-> ring-req identity-credentials (generate-deposit ?data) ?reply-fn))
    
(defroutes deposit-routes
  (GET "/all" req (page-with-sidebar req (deposits-page :all)))
  (GET "/finished" req (page-with-sidebar req (deposits-page :finished)))
  (GET "/failed" req (page-with-sidebar req (deposits-page :failed)))
  (GET "/incomplete" req (page-with-sidebar req (deposits-page :incomplete))))






