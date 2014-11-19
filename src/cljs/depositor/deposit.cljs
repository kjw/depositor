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
   "PDF"
   "application/vnd.crossref.patent-citations+tab-separated-values+g-zip" 
   "Patent Citations"
   "application/vnd.crossref.patent-citations+tab-separated-values"
   "Patent Citations"
   "application/vnd.crossref.patent-citations+csv+g-zip"
   "Patent Citations"
   "application/vnd.crossref.patent-citations+csv"
   "Patent Citations"
   "application/vnd.crossref.partial+xml"
   "Resource XML"
   "application/vnd.crossref.deposit+xml"
   "Deposit XML"})

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

(defn editing-citation-doi [citations]
  (get-in citations [:list (:editing citations) :match :DOI]))

(defn change-citation-text [citations s query-chan]
  (om/update! citations
              [:list (:editing @citations) :text]
              s)
  (put! query-chan s))

(defn change-citation-match [citations match]
  (om/update! citations
              [:list (:editing @citations) :match]
              match))

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

(defn match-selector [& {:keys [selected] :or {selected false}}]
  (let [clz (if selected "text-success" "text-grey")
        icon (if selected :ok-circle :record)]
    (dom/div
     (dom/h1 {:class clz}
             (util/icon icon)))))

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
     (dom/label "Suggested DOI matches")
     (dom/table
      {:class "table table-hover table-hover-pointer"}
      (dom/tbody
       (for [result (:results citations)]
         (dom/tr
          {:on-click #(change-citation-match citations result)}
          (dom/td (match-selector
                   :selected (= (:DOI result)
                                (editing-citation-doi citations))))
          (dom/td (match-details result)))))))
    (dom/p
     (dom/div
      {:class "pull-right"}
      (dom/p
       (dom/button {:class "btn btn-default"
                    :style {:margin-right "5px"}
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
  (dom/h1
   {:class "text-center" :style {:margin-left "10px"}}
   (condp = (:status deposit)
     "submitted" (dom/span {:class "text-muted"} (util/icon :repeat))
     "completed" (dom/span {:class "text-success"} (util/icon :ok-circle))
     "failed" (dom/span {:class "text-danger"} (util/icon :remove-circle)))))

(defn deposit-info-row [label val]
  (dom/div
   {:class "row" :style {:font-size ".9em"}}
   (dom/div
    {:class "col-md-7 text-right"
     :style {:padding-right "0"}}
    (dom/b label))
   (dom/div
    {:class "col-md-5"}
    (dom/span val))))

(defn deposit-doi-count [deposit]
  (dom/div
   {:style {:margin-top "10px" :margin-right "15px"}}
   (when-not (-> deposit :dois nil?)
     (deposit-info-row "DOIs" (-> deposit :dois count)))
   (when-not (-> deposit :citations nil?)
     (deposit-info-row "Citations" (-> deposit :citations count)))
   (when-not (-> deposit :citations nil?)
     (deposit-info-row
      "Matched"
      (count
       (filter #(-> % :match nil? not) (:citations deposit)))))
   (when-not (-> deposit :length nil?)
     (deposit-info-row
      "Size"
      (-> deposit :length (/ 1024) int (str "KiB"))))))

(defn deposit-labels [deposit]
  (dom/h4
   (dom/ul
    {:class "list-inline"}
    (dom/li
     (dom/span {:class "label label-default"}
               (-> deposit :content-type deposit-types)))
    (when (:test deposit)
      (dom/li
       (dom/span {:class "label label-warning"} "Test"))))))
  
(defn deposit-created [deposit]
  (dom/div
   (dom/span
    {:class "small"}
    (-> deposit :submitted-at util/friendly-date))))

(defn deposit-details [deposit]
  (dom/form
   {:class "form-horizontal"}
   (dom/div
    {:class "form-group"}
    (dom/label {:class "col-sm-3 control-label"} "Deposit ID")
    (dom/div
     {:class "col-sm-9" :style {:margin-top "8px"}}
     (dom/a
      {:href (str "https://api.crossref.org/v1/deposits/" (:batch-id deposit))}
      (:batch-id deposit))))
   (when (:filename deposit)
     (dom/div
      {:class "form-group"}
      (dom/label {:class "col-sm-3 control-label"} "Filename")
      (dom/div
       {:class "col-sm-9" :style {:margin-top "8px"}}
       (dom/a
        {:href (str "https://api.crossref.org/v1/deposits/"
                    (:batch-id deposit)
                    "/data")}
        (:filename deposit)))))))

(defn deposit-dois [deposit]
  (dom/table
   {:class "table"}
   (dom/thead
    (dom/tr (dom/th "DOI") (dom/th "")))
   (dom/tbody
    (for [doi (:dois deposit)]
      (dom/tr
       (dom/td doi)
       (dom/td
        (dom/ul
         {:class "list-inline small text-right"}
         (dom/li
          (dom/a {:href (str "http://api.crossref.org/works/" doi)}
                 (util/icon :file) " JSON"))
         (dom/li
          (dom/a {:href (str "http://api.crossref.org/works/" doi ".xml")}
                 (util/icon :file) " XML"))
         (dom/li
          (dom/a {:href (str "http://search.crossref.org/?q=" doi)}
                 (util/icon :search) " Metadata Search"))
         (dom/li
          (dom/a {:href (str "http://dx.doi.org/" doi)}
                 (util/icon :new-window) " Resolve DOI")))))))))

(defn deposit-title [deposit]
  (dom/h4
   (or (:title deposit) (:filename deposit) (:batch-id deposit))))

(defn deposit-citations [deposit citation-chan]
  (dom/table
   {:class "table table-hover table-hover-pointer"}
   (dom/thead
    (dom/tr (dom/th "#")
            (dom/th "Citation text")
            (dom/th "Matched to")))
   (dom/tbody
    (for [c (:citations deposit)]
      (dom/tr
       {:on-click #(put! citation-chan {:list (:citations @deposit) :editing 0})}
       (dom/td
        (when (:number c) (dom/h5 (str (:number c) "."))))
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

(defn deposit-dois? [deposit] (not (nil? (:dois deposit))))
(defn deposit-submission? [deposit] (not (nil? (:submission deposit))))
(defn deposit-citations? [deposit] (not (nil? (:citations deposit))))
(defn deposit-handoff? [deposit] (not (nil? (:handoff deposit))))

(defcomponent deposit [deposit owner]
  (render-state
   [_ {:keys [open-chan citation-chan]}]
   (let [tabs
         (concat
          (when (deposit-dois? deposit)
            [{:name :dois
              :label (dom/span
                      "DOIs in Deposit "
                      (dom/span
                       {:class "badge"}
                       (count (:dois deposit))))
              :content (deposit-dois deposit)}])
          (when (deposit-citations? deposit)
            [{:name :citations
              :label (dom/span
                      "Extracted Citations "
                      (dom/span
                       {:class "badge"}
                       (count (:citations deposit))))
              :content (deposit-citations deposit citation-chan)}])
          (when (deposit-submission? deposit)
            [{:name :submission
              :label "Submission Log"
              :content (deposit-submission deposit)}]))
         tabs-with-active (concat
                           [(assoc (first tabs) :active true)]
                           (drop 1 tabs))]
     (dom/div
      {:class "fadein"}
      (dom/div
       {:style {:margin-bottom "20px"}}
       (when (deposit-citations? deposit)
         (dom/button
          {:class "btn btn-success btn-sm pull-right"}
          (util/icon :cloud-upload)
          " Deposit citations"))
       (dom/a
        {:href "#" :on-click #(put! open-chan {})}
        (util/icon :arrow-left) " Back"))
      (if (:title deposit)
        (util/in-panel (deposit-details deposit) :title (:title deposit))
        (util/in-panel (deposit-details deposit)))
      (util/tabs tabs-with-active)))))
    
(defcomponent deposit-item [deposit owner]
  (render-state [_ {:keys [open-chan]}]
                (dom/tr
                 {:on-click #(put! open-chan deposit)}
                 (dom/td
                  (dom/div
                   {:class "row"}
                   (dom/div {:class "col-md-1"}
                            (deposit-status deposit))
                   (dom/div {:class "col-md-8"}
                            (deposit-title deposit)
                            (deposit-labels deposit)
                            (deposit-created deposit))
                   (dom/div {:class "col-md-3"}
                            (deposit-doi-count deposit)))))))

(defn change-deposits-page [app]
  (fn [page-number]
    (send-and-update! [::deposits {:rows 10 :offset (* 10 (dec page-number))}]
                      app
                      :deposits)))

(defn deposit-list-selectors []
  (dom/ul
   {:class "list-inline"}
   (dom/li
    (util/dropdown-selector "Status" ["All" "In progress" "Finished" "Failed"]))
   (dom/li
    (util/dropdown-selector "Type" ["All" "PDF" "Deposit XML" "Resource XML" "Patent Citations"]))
   (dom/li
    (util/dropdown-selector "Test" ["All" "Yes" "No"]))
   (dom/li
    {:class "pull-right"}
    (util/dropdown-selector "Order" ["Descending" "Ascending"]))))

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
                  (deposit-list-selectors)
                  (dom/table {:class "table table-hover table-hover-pointer"}
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
  (dom/div {:style {:margin-top "20px"}}
   (dom/h4
    "Extract the reference list from a PDF and add to an existing DOI.")
   (dom/button
    {:type "button"
     :style {:margin-top "30px"}
     :on-click (file-picker
                #js ["application/pdf"]
                #(doseq [blob (js->clj % :keywordize-keys true)]
                   (ws/send! [::deposit-link
                              {:url (:url blob)
                               :content-type "application/pdf"
                               :filename (:filename blob)}]
                             (partial add-local-deposit app))))
     :class "btn btn-block btn-success btn-lg"}
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













