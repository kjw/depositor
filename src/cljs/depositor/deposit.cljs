(ns depositor.deposit
  (:require-macros [cljs.core.async.macros :refer (go go-loop)])
  (:require [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om.core :as om]
            [cljs.core.async :refer (<! >! put! chan sliding-buffer close!)]
            [depositor.util :as util]
            [depositor.ws :as ws :refer [send-and-update!]]))

;; filter on:
;; type
;; status
;; test?

;; search on:
;; doi search
;; title search

;; order by:
;; deposit data asc, desc

;; paging

(def deposit-types
  {"application/pdf" 
   "Reference List from PDF"
   "application/vnd.crossref.patent-citations+tab-separated-values+g-zip" 
   "Patent Citations"
   "application/vnd.crossref.patent-citations+tab-separated-values"
   "Patent Citations"
   "application/vnd.crossref.patent-citations+csv+g-zip"
   "Patent Citations"
   "application/vnd.crossref.patent-citations+csv"
   "Patent Citations"
   "application/vnd.crossref.partial+xml"
   "Partial Deposit XML"
   "application/vnd.crossref.deposit+xml"
   "Full Deposit XML"})

(def page-state
  (atom {:deposits nil
         :deposit {}
         :citations {}}))

;; status - completed failed submitted
;; time of submission
;; test?
;; content type
;; length
;; (submission history)
;; (submission errors)
;; (dois)

(defn editing-citation-text [citations]
  (get-in citations [:list (:editing citations) :text]))

(defn change-citation-text [citations s query-chan]
  (om/update! citations
              [:list (:editing @citations) :text]
              s)
  (put! query-chan s))

(defn match-details [match]
  (dom/div
   (dom/h6 (-> match :title first))
   (dom/p
    {:class "small"}
    (str (-> match (get-in [:issued :date-parts]) ffirst)
         " - "
         (-> match :container-title first)))
   (dom/p
    (dom/a
     {:href (str "http://dx.doi.org/" (:DOI match))}
     (:DOI match)))))

(defn citation-view [citations citation-chan query-chan]
  (dom/div
   {:class "fadein"}
   (dom/form
    {:role "form"}
    (dom/div
     {:class "form-group"}
     (dom/label {:for "citation-text"} "Citation text")
     (dom/textarea {:class "form-control lead"
                    :rows "3"
                    :value (editing-citation-text citations)
                    :on-change #(change-citation-text citations
                                                      (.-value (.-target %))
                                                      query-chan)}))
    (dom/div
     {:class "form-group"}
     (dom/label "Potential DOI matches")
     (dom/table
      {:class "table"}
      (dom/tbody
       (for [result (:results citations)]
         (dom/tr (dom/td (match-details result)))))))
    (dom/p
     (dom/div {:class "pull-right"}
              (dom/div
               (dom/button {:class "btn btn-default"
                            :on-click #(put! citation-chan {})}
                           "Discard changes")
               (dom/button {:class "btn btn-success"} "Save")))))))

(defcomponent citation [citations owner]
  (init-state [_] {:query-chan (chan (sliding-buffer 1))})
  (will-mount [_]
              (let [query-chan (om/get-state owner :query-chan)]
                (put! query-chan (editing-citation-text citations))
                ;; only take every 750 ms
                (go-loop [text (<! query-chan)]
                  (send-and-update!
                   [::citation-match {:text text}]
                   citations
                   :results)
                  (recur (<! query-chan)))))
  (render-state [_ {:keys [citation-chan query-chan]}]
                (citation-view citations citation-chan query-chan)))

(defn deposit-status [deposit]
  (condp = (:status deposit)
    "submitted" (dom/span {:class "text-info glyphicon glyphicon-inbox"})
    "completed" (dom/span {:class "text-success glyphicon glyphicon-ok"})
    "failed" (dom/span {:class "text-danger glyphicon glyphicon-remove"})))

(defn deposit-doi-count [deposit]
  (when-not (nil? (:dois deposit))
    (dom/span (dom/b (str (count (:dois deposit)) " DOIs")))))

(defn deposit-labels [deposit]
  (dom/div
   (dom/span {:class "label label-default"}
             (-> deposit :content-type deposit-types))
   (when (:test deposit)
     (dom/span {:class "label label-warning"} "Test"))))

(defn deposit-created [deposit]
  (dom/div
   (dom/span {:class "small"} (str "Created " (:submitted-at deposit)))))

(defn deposit-details [deposit]
  (dom/div nil))

(defn deposit-dois [deposit]
  (dom/table
   {:class "table"}
   (for [doi (:dois deposit)]
     (dom/tr (dom/td doi)))))

(defn deposit-citations [deposit citation-chan]
  (dom/table
   {:class "table table-hover table-hover-pointer"}
   (dom/thead
    (dom/tr (dom/th "Citation text")
            (dom/th "Matched to")))
   (dom/tbody
    (for [c (:citations deposit)]
      (dom/tr
       {:on-click #(put! citation-chan {:list (:citations @deposit) :editing 0})}
       (dom/td (:text c))
       (dom/td
        (if (:match c)
          (match-details (:match c))
          (dom/p "Not matched"))))))))

(defn deposit-submission [deposit]
  (dom/table
   {:class "table"}
   (dom/tr
    (dom/th "Status")
    (dom/th "Message")
    (dom/th "Related DOI"))
   (for [msg (get-in deposit [:submission :messages])]
     (dom/tr
      (dom/td (:status msg))
      (dom/td
       (str
        (when (:message-type msg) (str (:message-type msg) " - "))
        (:message msg)))
      (dom/td (when (:related-doi msg) (:related-doi msg)))))))

(defcomponent deposit [deposit owner]
  (render-state
   [_ {:keys [open-chan citation-chan]}]
   (dom/div
    {:class "fadein"}
    (dom/div
     {:class "well well-sm"}
     (dom/a
      {:href "#" :on-click #(put! open-chan {})}
      "Back to list"))
    (util/in-panel "Details" (deposit-details deposit))
    (util/tabs
     [{:name :dois
       :label "DOIs in Deposit"
       :content (deposit-dois deposit)
       :active true}
      {:name :citations
       :label "Extracted Citations"
       :content (deposit-citations deposit citation-chan)}
      {:name :submission
       :label "Submission Log"
       :content (deposit-submission deposit)}]))))
    
(defcomponent deposit-item [deposit owner]
  (render-state [_ {:keys [open-chan]}]
                (dom/li {:class "row list-group-item"}
                        (dom/a {:href "#"
                                :on-click #(put! open-chan deposit)}
                               (dom/div {:class "col-md-1"}
                                        (deposit-status deposit))
                               (dom/div {:class "col-md-9"}
                                        (deposit-labels deposit)
                                        (deposit-created deposit))
                               (dom/div {:class "col-md-2"}
                                        (deposit-doi-count deposit))))))

(defn change-deposits-page [app]
  (fn [page-number]
    (send-and-update! [::deposits {:rows 10 :offset (* 10 (dec page-number))}]
                      app
                      :deposits)))

(defcomponent deposit-list [app owner]
  (init-state [_] {:open-chan (chan)
                   :citation-chan (chan)})
  (will-mount [_]
              (send-and-update! [::deposits {:rows 10}] app :deposits)
              (let [open-chan (om/get-state owner :open-chan)]
                (go-loop [deposit (<! open-chan)]
                  (om/update! app :deposit deposit)
                  (recur (<! open-chan))))
              (let [citation-chan (om/get-state owner :citation-chan)]
                (go-loop [citations (<! citation-chan)]
                  (om/update! app :citations citations)
                  (recur (<! citation-chan)))))
  (render-state [_ {:keys [open-chan citation-chan]}]
                (cond
                 (not (empty? (:citations app)))
                 (om/build citation
                           (:citations app)
                           {:init-state {:citation-chan citation-chan}})
                 
                 (not (empty? (:deposit app)))
                 (om/build deposit
                           (:deposit app)
                           {:init-state {:open-chan open-chan
                                         :citation-chan citation-chan}})

                 (not (nil? (:deposits app)))
                 (dom/div
                   {:class "fadein"}
                   (dom/ul {:class "list-group"}
                           (om/build-all
                            deposit-item
                            (get-in app [:deposits :items])
                            {:init-state {:open-chan open-chan}}))
                   (util/paginate-list (:deposits app)
                                       (change-deposits-page app)))

                 :else
                 (util/loader))))

(defn file-picker [content-types success-fn]
  (fn []
    (.setKey js/filepicker "AQPGwFAMcSlOZrfrwUT0sz")
    (.pickMultiple
     js/filepicker
     #js {:mimetypes content-types
          :container "modal"
          :multiple true
          :folders true
          :services #js ["COMPUTER" "BOX" "DROPBOX" "GITHUB"
                         "GOOGLE_DRIVE" "GMAIL" "URL" "FTP" "WEBDAV"]}
     #(success-fn %))))

(defn add-local-deposit [app deposit]
  (om/transact! app [:deposits :items] #(into [deposit] %)))

(defn pdf-upload [app]
  (dom/div
   (dom/p        
    "Extract the reference list from a PDF and add to an existing DOI.")
   (dom/button
    {:type "button"
     :on-click (file-picker
                #js ["application/pdf"]
                #(doseq [blob (js->clj % :keywordize-keys true)]
                   (ws/send! [::deposit-link
                              {:url (:url blob)
                               :content-type "application/pdf"
                               :filename (:filename blob)}]
                             (partial add-local-deposit app))))
     :class "btn btn-primary btn-lg"}
    (util/icon :upload)
    " Upload PDFs")))

(defn doi-list-upload [app]
  (dom/div
   (dom/p
    (str "Add the same metadata to a list of DOIs,"
         " for example a license or full-text link"
         " regular expression."))))

(defn xml-upload [app]
  (dom/div
   (dom/p
    "Upload CrossRef XML to register or update DOIs. The"
    " XML should conform to either the"
    (dom/a {:href ""} " full deposit")
    " schema or the"
    (dom/a {:href ""} " resource deposit")
    "schema." )
   (dom/button {:type "button"
                :on-click (file-picker #js ["text/xml" "application/xml"] nil)
                :class "btn btn-primary btn-lg"}
               (util/icon :upload)
               " Upload CrossRef XML")))

(defn upload-modal [app]
  (dom/div
   {:class "modal fade"
    :id "upload-modal"
    :tabindex "-1"
    :role "dialog"
    :aria-hidden "true"}
   (dom/div
    {:class "modal-dialog"}
    (dom/div
     {:class "modal-content"}
     (dom/div
      {:class "modal-header"}
      (dom/button {:type "button" :class "close" :data-dismiss "modal"}
                  (dom/span {:aria-hidden "true"} (util/icon :remove))
                  (dom/span {:class "sr-only"} "Close"))
      (dom/h4 {:class "modal-title"}
              "Upload "
              (dom/span {:class "small"} "Create a new deposit")))
     (dom/div
      {:class "modal-body"}
      (util/tabs
       [{:name :pdf
         :label "PDF Citations"
         :active true
         :content (pdf-upload app)}
        {:name :xml
         :label "CrossRef XML"
         :content (xml-upload app)}
        {:name :doi-list
         :label "DOI List"
         :content (doi-list-upload app)}]))
     (dom/div
      {:class "modal-footer"}
      (dom/button {:type "button"
                   :class "btn btn-default"
                   :data-dismiss "modal"}
                  "Close"))))))

(defcomponent upload [app owner]
  (render-state
   [_ {:keys []}]
   (upload-modal app)))
   
(when-let [e (.getElementById js/document "deposits")]
  (om/root deposit-list
           page-state
           {:target e}))

(when-let [e (.getElementById js/document "deposit")]
  (om/root deposit
           page-state
           {:target e}))

(when-let [e (.getElementById js/document "upload")]
  (om/root upload
           page-state
           {:target e}))













