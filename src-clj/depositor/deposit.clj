(ns depositor.deposit
  (:require [clojure.string :as string]
            [hiccup.core :refer [html]]
            [compojure.core :refer [defroutes GET]]
            [org.httpkit.client :as hc]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.java.io :refer [reader]]
            [depositor.event :refer [handle-socket-event]]
            [depositor.generate :refer [citation-deposit]]
            [environ.core :refer [env]]
            [clj-time.format :as tf]
            [clj-time.coerce :as tco]
            [depositor.layout :refer [identity-credentials page-with-sidebar]]))

(def test-deposits (= "true" (env :test-deposits)))

(def deposit-submit-time-format
  (tf/formatter "EEE MMM dd HH:mm:ss ZZZ yyyy"))

(declare get-deposit)

;; Below we choose an alternative +2GMT timezone to replace CEST,
;; which is not understood by clj-time.
(defn reformat-submit-time [deposit]
  (update-in
   deposit
   [:submitted-at]
   #(let [no-cest-bst (string/replace % #"CEST|BST|EDT" "EET")]
      (->> no-cest-bst (tf/parse deposit-submit-time-format) tco/to-long))))

(defn expand-children [credentials deposit]
  (if-not (:children deposit)
    deposit
    (update-in
     deposit
     [:children]
     #(map (fn [bid] (get-deposit credentials bid :expand false)) %))))

(defn expand-parent [credentials deposit]
  (if-not (:parent deposit)
    deposit
    (update-in
     deposit
     [:parent]
     #(get-deposit credentials % :expand false))))

(defn prepare-deposit [deposit credentials
                       & {:keys [expand] :or {expand true}}]
  (if-not expand
    (reformat-submit-time deposit)
    (->> deposit
         (expand-parent credentials)
         (expand-children credentials)
         reformat-submit-time)))

(defn prepare-filters [filterm]
  (->> filterm
       (filter (fn [[n vs]] (not (nil? vs))))
       (map (fn [[n vs]] (str (name n) ":" vs)))
       (string/join ",")))

(defn prepare-params [params]
  (let [prepared-filters (-> params :filter prepare-filters)]
    (if (string/blank? prepared-filters)
      (dissoc params :filter)
      (assoc params :filter prepared-filters))))

(defn get-deposits [{:keys [username password] :as creds} params]
  (update-in
   (-> (env :api)
       (str "/v1/deposits")
       (hc/get {:basic-auth [username password]
                :query-params (prepare-params params)})
       deref
       :body
       (json/read-str :key-fn keyword)
       :message)
    [:items]
    #(map prepare-deposit % (repeat creds))))

(defn get-deposit [{:keys [username password] :as creds} id
                   & {:keys [expand] :or {expand true}}]
  (-> (str (env :api) "/v1/deposits/" id)
      (hc/get {:basic-auth [username password]})
      deref
      :body
      (json/read-str :key-fn keyword)
      :message
      (prepare-deposit creds :expand expand)))

(defn put-deposit [{:keys [username password] :as creds}
                   {:keys [url filename content-type test] :or {test test-deposits}}]
  (let [{:keys [headers status body error]}
        (-> (str (env :api) "/v1/deposits")
            (hc/post {:basic-auth [username password]
                      :query-params {:url url
                                     :test test
                                     :filename filename}
                      :headers {"Content-Type" content-type}})
            deref)]
    (-> body
        (json/read-str :key-fn keyword)
        :message
        (prepare-deposit creds))))

(defn get-citation-matches [{:keys [text]}]
  (let [clean-text (string/replace text #"(?U)[^\w]+" " ")]
    (-> (str (env :api) "/v1/works")
        (hc/get {:query-params {:query clean-text :rows 4}})
        deref
        :body
        (json/read-str :key-fn keyword)
        :message
        :items)))

(defn get-doi [doi-text]
  (let [{:keys [body error status]}
        (->> doi-text
             (str (env :api) "/v1/works/")
             hc/get
             deref)]
    (if (or error (not= 200 status))
      {}
      (-> body
          (json/read-str :key-fn keyword)
          :message))))

;; httpkit client does not drop the body on following a redirect,
;; so for now we manually follow the redirect.

(defn generate-deposit [{:keys [username password] :as creds}
                        {:keys [parent test doi citations] :or {test test-deposits}}]
  (let [{:keys [headers status]}
        (-> (str (env :api) "/v1/deposits")
            (hc/post {:basic-auth [username password]
                      :query-params {:parent parent :test test}
                      :headers {"Content-Type" "application/vnd.crossref.partial+xml"}
                      :body (citation-deposit doi citations)
                      :follow-redirects false})
            deref)]
    (when (#{301 302 303 307 308} status)
      (-> (str (env :api) (:location headers))
          (hc/get {:basic-auth [username password]})
          deref
          :body
          (json/read-str :key-fn keyword)
          :message
          (prepare-deposit creds)))))

(defn put-citations [{:keys [username password]}
                     {:keys [id citations]}]
  (let [{:keys [body status]}
        (-> (str (env :api) "/v1/deposits/" id)
            (hc/post {:basic-auth [username password]
                      :headers {"Content-Type" "application/json"}
                      :body (-> citations json/write-str)
                      :follow-redirects false})
            deref)]
    (if (#{200 201} status)
      {:status :completed}
      {:status :failed})))

(defn deposit-page [batch-id]
  [:div {:style "margin-top: 30px"}
   [:div#deposits-page
    [(keyword (str "div#deposit." batch-id))]]])

(defn deposits-page [t]
  [:div {:style "margin-top: 30px"}
   [:div#deposits-page
    [(keyword (str "span#deposits-status." (name t)))]
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

(defmethod handle-socket-event ::citations
  [{:keys [ring-req ?reply-fn ?data]}]
  (-> ring-req identity-credentials (put-citations ?data) ?reply-fn))

(defmethod handle-socket-event ::generate-deposit
  [{:keys [ring-req ?reply-fn ?data]}]
  (-> ring-req identity-credentials (generate-deposit ?data) ?reply-fn))

(defmethod handle-socket-event ::lookup
  [{:keys [ring-req ?reply-fn ?data]}]
  (-> ?data :text get-doi ?reply-fn))
    
(defroutes deposit-routes
  (GET "/all" req (page-with-sidebar req (deposits-page :all)))
  (GET "/finished" req (page-with-sidebar req (deposits-page :completed)))
  (GET "/failed" req (page-with-sidebar req (deposits-page :failed)))
  (GET "/incomplete" req (page-with-sidebar req (deposits-page :submitted)))
  (GET "/:batch-id" [batch-id :as req] (page-with-sidebar req (deposit-page batch-id))))






