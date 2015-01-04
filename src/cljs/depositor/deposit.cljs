(ns depositor.deposit
  (:require-macros [cljs.core.async.macros :refer (go go-loop)])
  (:require [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om.core :as om]
            [cljs.core.async :refer (<! >! put! chan sliding-buffer close!)]
            [taoensso.sente :refer (cb-success?)]
            [depositor.path :refer [path-to]]
            [depositor.util :as util]
            [depositor.notify :as notify]
            [depositor.ws :as ws :refer [send-and-update!]]))

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

(def initial-deposit-status-filter
  (if-let [e (.getElementById js/document "deposits-status")]
    (keyword (.-className e))
    :all))

(def page-state
  (atom {:query {:rows 10 :sort "submitted" :order "desc"}
         :dropdowns {:status initial-deposit-status-filter
                     :test :all
                     :type :all
                     :sort-by :desc}
         :deposits nil
         :deposit {}
         :citations {}
         :lookup {:text ""
                  :result {}}}))

(defn editing-citation-text [citations]
  (get-in citations [:list (:position citations) :text]))

(defn editing-citation-number [citations]
  (get-in citations [:list (:position citations) :number]))

(defn editing-citation-doi [citations]
  (get-in citations [:list (:position citations) :match :DOI]))

(defn change-citation-text [citations s query-chan]
  (om/update! citations
              [:list (:position @citations) :text]
              s)
  (put! query-chan s))

(defn change-citation-number [citations s]
  (om/update! citations [:list (:position @citations) :number] s))

(defn change-citation-match [citations match]
  (om/update! citations
              [:list (:position @citations) :match]
              match))

(defn clear-citation-match [citations]
  (om/update! citations [:list (:position @citations) :match] nil))

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

(defn citation-view [citations citation-chan save-citations-chan query-chan]
  (dom/div
   {:class "fadein"}
   (dom/form
    {:role "form"}
    (dom/div
     {:class "form-group"}
     (dom/label {:for "citation-number"} "Citation number")
     (dom/input {:type "text" :class "form-control"
                 :value (editing-citation-number citations)
                 :on-change #(change-citation-number citations
                                                     (.-value (.-target %)))}))
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
         (if (= (:DOI result) (editing-citation-doi citations))
           (dom/tr
            {:on-click #(clear-citation-match citations)}
            (dom/td (match-selector :selected true))
            (dom/td (match-details result)))
           (dom/tr
            {:on-click #(change-citation-match citations @result)}
            (dom/td (match-selector :selected false))
            (dom/td (match-details result))))))))
    (dom/p
     (dom/div
      {:class "pull-right"}
      (dom/p
       (dom/a {:class "btn btn-default"
               :style {:margin-right "5px"}
               :on-click #(put! citation-chan {})}
              "Discard changes")
       (dom/a {:class "btn btn-success"
               :on-click #(put! save-citations-chan (:list @citations))}
              "Save")))))))

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
  (render-state [_ {:keys [citation-chan query-chan save-citations-chan]}]
                (citation-view citations
                               citation-chan
                               save-citations-chan
                               query-chan)))

(defn deposit-status [deposit]
  (dom/h1
   {:class "text-center" :style {:margin-left "10px"}}
   (condp = (:status deposit)
     "submitted" (dom/span {:class "text-muted"} (util/icon :repeat))
     "completed" (dom/span {:class "text-success"} (util/icon :ok-circle))
     "failed" (dom/span {:class "text-danger"} (util/icon :remove-circle)))))

(defn deposit-status-small [deposit]
  (condp = (:status deposit)
     "submitted" (dom/span {:class "text-muted"} (util/icon :repeat))
     "completed" (dom/span {:class "text-success"} (util/icon :ok-circle))
     "failed" (dom/span {:class "text-danger"} (util/icon :remove-circle))))

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

(defn deposit-counts [deposit]
  (dom/div
   {:style {:margin-top "10px" :margin-right "15px"}}
   (when-not (-> deposit :dois nil?)
     (deposit-info-row "DOIs" (-> deposit :dois count)))
   (when (and (= "application/pdf" (:content-type deposit))
              (= "completed" (:status deposit)))
     (deposit-info-row "Citations" (:citation-count deposit)))
   (when (and (= "application/pdf" (:content-type deposit))
              (= "completed" (:status deposit)))
     (deposit-info-row "Matched" (:matched-citation-count deposit)))
   (when-not (-> deposit :children nil?)
     (deposit-info-row "Deposits" (-> deposit :children count)))
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
    (when (:parent deposit)
      (dom/li
       (dom/span {:class "label label-default"}
                 "From PDF Citations")))
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
   (when (:title deposit)
     (dom/div
      {:class "form-group"}
      (dom/label {:class "col-sm-2 control-label"} "Title")
      (dom/div
       {:class "col-sm-10" :style {:margin-top "8px"}}
       (dom/span (:title deposit)))))
   (dom/div
    {:class "form-group"}
    (dom/label {:class "col-sm-2 control-label"} "Deposit ID")
    (dom/div
     {:class "col-sm-10" :style {:margin-top "8px"}}
     (dom/a
      {:href (path-to "deposits" (:batch-id deposit))}
      (:batch-id deposit))))
   (when (:filename deposit)
     (dom/div
      {:class "form-group"}
      (dom/label {:class "col-sm-2 control-label"} "Filename")
      (dom/div
       {:class "col-sm-10" :style {:margin-top "8px"}}
       (dom/a
        {:href (str "https://api.crossref.org/v1/deposits/"
                    (:batch-id deposit)
                    "/data")}
        (:filename deposit)))))
   (dom/div
    {:class "form-group"}
    (dom/label {:class "col-sm-2 control-label"} "Created")
    (dom/div
     {:class "col-sm-10" :style {:margin-top "8px"}}
     (dom/span (-> deposit :submitted-at util/friendly-date))))))

(defn deposit-parent [deposit]
  (-> deposit :parent deposit-details))

(defn deposit-dois [deposit]
  (dom/table
   {:class "table"}
   (dom/thead
    (dom/tr (dom/th "DOI") (dom/th "")))
   (dom/tbody
    (for [doi (:dois deposit)]
      (dom/tr
       (dom/td
        (dom/a {:href (str "http://dx.doi.org/" doi)} doi))
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
                 (util/icon :search) " Metadata Search")))))))))

(defn deposit-title-text [deposit]
  (or (:title deposit)
      (:filename deposit)
      (when (= (-> deposit :dois count) 1) (-> deposit :dois first))
      (:batch-id deposit)))

(defn deposit-title [deposit]
  (dom/h4 (deposit-title-text deposit)))

(defn deposit-citations [deposit citation-chan]
  (dom/table
   {:class "table table-hover table-hover-pointer"}
   (dom/thead
    (dom/tr (dom/th "#")
            (dom/th "Citation text")
            (dom/th "Matched to")))
   (dom/tbody
    (for [pos (range (-> deposit :citations count))
          :let [c (-> deposit :citations (nth pos))]]
      (dom/tr
       {:on-click #(put! citation-chan {:list (:citations @deposit) :position pos})}
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

(defn deposit-children [deposit]
  (dom/table
   {:class "table"}
   (dom/tr
    (dom/th "Status")
    (dom/th "Deposit ID")
    (dom/th "For DOI")
    (dom/th "Created")
   (for [child (:children deposit)]
     (dom/tr
      (dom/td (deposit-status-small child))
      (dom/td (dom/a {:href (path-to "deposits" (:batch-id child))} (:batch-id child)))
      (dom/td (-> child :dois first))
      (dom/td (-> child :submitted-at util/friendly-date)))))))

(defn deposit-handoff [deposit]
  (dom/ul
   (dom/li
    (str "Status: "
         (get-in deposit [:handoff :status])))
   (dom/li
    (str "Status updated at: "
         (util/friendly-date (get-in deposit [:handoff :timestamp]))))
   (dom/li
    (str "Try count: "
         (get-in deposit [:handoff :try-count])))
   (dom/li
    (str "Next try in (if applicable): "
         (/ (get-in deposit [:handoff :delay-millis]) 1000) " seconds"))))

(defn deposit-dois? [deposit] (not (nil? (:dois deposit))))
(defn deposit-submission? [deposit] (not (nil? (:submission deposit))))
(defn deposit-citations? [deposit] (not (nil? (:citations deposit))))
(defn deposit-handoff? [deposit] (not (nil? (:handoff deposit))))
(defn deposit-children? [deposit] (not (nil? (:children deposit))))

(defn save-citations [app citations citation-chan]
  (let [id (get-in @app [:deposit :batch-id])
        nid (notify/add-message :saving)]
    (ws/send!
     [::citations {:id id :citations citations}]
     (fn [reply]
       (notify/finish-message nid)
       (if (and (cb-success? reply) (= (:status reply) :completed))
         (do
           (println "success")
           (om/update! app [:deposit :citations] citations)
           (put! citation-chan {}))
         (do
           (println "error")
           (put! citation-chan {})))))))

(defcomponent deposit [app owner]
  (init-state [_] {:citation-chan (chan) :save-citations-chan (chan)})
  (will-mount
   [_]
   (let [citation-chan (om/get-state owner :citation-chan)
         save-citations-chan (om/get-state owner :save-citations-chan)]
     (go-loop [citations (<! citation-chan)]
       (om/update! app :citations citations)
       (recur (<! citation-chan)))
     (go-loop [citations (<! save-citations-chan)]
       (save-citations app citations citation-chan)
       (recur (<! save-citations-chan))))
   (ws/send-and-update!
    [::deposit {:id (.-className (.getElementById js/document "deposit"))}]
    app
    :deposit))
  (render-state
   [_ {:keys [open-chan citation-chan
              save-citations-chan]}]
   (cond
    (not (empty? (:citations app)))
    (om/build citation
              (:citations app)
              {:init-state {:citation-chan citation-chan
                            :save-citations-chan save-citations-chan}})

    (-> app :deposit empty? not)
    (let [deposit (:deposit app)
          tabs
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
               :content (deposit-submission deposit)}])
           (when (deposit-handoff? deposit)
             [{:name :handoff
               :label "Handoff Status"
               :content (deposit-handoff deposit)}])
           (when (deposit-children? deposit)
             [{:name :children
               :label (dom/span
                       "Citation Deposits "
                       (dom/span {:class "badge"} (-> deposit :children count)))
               :content (deposit-children deposit)}]))
          tabs-with-active (concat
                            [(assoc (first tabs) :active true)]
                            (drop 1 tabs))]
      (dom/div
       {:class "fadein"}
       (dom/h3 {:style {:margin-bottom "1.5em;"}} (deposit-title-text deposit))
       (when (deposit-citations? deposit)
         (dom/div
          {:style {:margin-bottom "20px"}}
          (dom/a
           {:class "btn btn-success btn-sm pull-right"
            :data-target "#citation-deposit-modal"
            :data-toggle "modal"}
           (util/icon :cloud-upload)
           " Create citations deposit")))
       (util/in-panel (deposit-details deposit) :title "Details")
       (when (:parent deposit)
         (util/in-panel (deposit-parent deposit) :title "Generated From PDF Deposit"))
       (util/tabs tabs-with-active)))

    :else
    (util/loader))))
    
(defcomponent deposit-item [deposit owner]
  (render-state [_ {:keys [open-chan]}]
                (dom/tr
                 {:on-click #(set! (.-location js/window) (path-to "deposits" (:batch-id deposit)))}
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
                            (deposit-counts deposit)))))))

(defn change-deposits-page [app]
  (fn [page-number]
    (let [old-query (-> app deref :query)
          new-query (assoc old-query :offset (* (:rows old-query)
                                                (dec page-number)))]
      (om/update! app :query new-query)
      (om/update! app :deposits nil)
      (ws/send-and-update! [::deposits new-query] app :deposits))))

(defn change-deposits-filter [app k value]
  (let [filter-val (if (= :all value) nil (name value))
        old-query (-> app deref :query)
        new-query (assoc-in old-query [:filter k] filter-val)]
    (om/update! app [:dropdowns k] value)
    (om/update! app :query new-query)
    (om/update! app :deposits nil)
    (ws/send-and-update! [::deposits new-query] app :deposits)))

(defn change-deposits-search [app name]
  (fn [value]
    (let [old-query (-> app deref :query)
          new-query (assoc old-query :query value)]
      (om/update! app :query new-query)
      (om/update! app :deposits nil)
      (ws/send-and-update! [::deposits new-query] app :deposits))))

(defn change-deposits-order [app order]
  (let [old-query (-> app deref :query)
        new-query (assoc old-query :order (name order))]
    (om/update! app [:dropdowns :sort-by] order)
    (om/update! app :query new-query)
    (om/update! app :deposits nil)
    (ws/send-and-update! [::deposits new-query] app :deposits)))

(defn deposit-list-selectors [app]
  (dom/ul
   {:class "list-inline"}
   (dom/li
    (util/dropdown-selector
     "Status"
     (get-in app [:dropdowns :status])
     (partial change-deposits-filter app :status)
     [{:label "All" :value :all}
      {:label "In progress" :value :submitted}
      {:label "Finished" :value :completed}
      {:label "Failed" :value :failed}]))
   (dom/li
    (util/dropdown-selector
     "Type"
     (get-in app [:dropdowns :type])
     (partial change-deposits-filter app :type)
     [{:label "All" :value :all}
      {:label "PDF" :value "application/pdf"}
      {:label "Deposit XML" :value "application/vnd.crossref.deposit+xml"}
      {:label "Resource XML" :value "application/vnd.crossref.partial+xml"}]))
   (dom/li
    (util/dropdown-selector
     "Test"
     (get-in app [:dropdowns :test])
     (partial change-deposits-filter app :test)
     [{:label "All" :value :all}
      {:label "Yes" :value :t}
      {:label "No" :value :f}]))
   (dom/li
    {:class "pull-right"}
    (util/dropdown-selector
     "Sort by"
     (get-in app [:dropdowns :sort-by])
     (partial change-deposits-order app)
     [{:label "Newest" :value :desc}
      {:label "Oldest" :value :asc}]))))

(defcomponent deposit-list [app owner]
  (init-state [_] {:open-chan (chan)})
  (will-mount [_]
              (let [query (if (= initial-deposit-status-filter :all)
                            {:rows 10 :sort "submitted" :order "desc"}
                            {:rows 10 :sort "submitted" :order "desc"
                             :filter {:status (name initial-deposit-status-filter)}})]
                (ws/send-and-update! [::deposits query] app :deposits))
              (let [open-chan (om/get-state owner :open-chan)]
                (go-loop [deposit (<! open-chan)]
                  (om/update! app :deposit deposit)
                  (recur (<! open-chan)))))
  (render-state [_ {:keys [open-chan citation-chan]}]
                (cond
                 
                 
                 (not (empty? (:deposit app)))
                 (om/build deposit
                           (:deposit app)
                           {:init-state {:open-chan open-chan
                                         :citation-chan citation-chan}})

                 (not (nil? (:deposits app)))
                 (dom/div
                  {:class "fadein"}
                  (deposit-list-selectors app)
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

(defn add-local-deposit [app message-id deposit]
  (om/transact! app [:deposits :items] #(into [deposit] %))
  (notify/finish-message message-id))

(defn pdf-upload [app]
  (dom/div {:style {:margin-top "50px"}}
   (dom/span {:class "lead"}
    "Extract the reference list from a PDF and add to an existing DOI.")
   (dom/button
    {:type "button"
     :style {:margin-top "40px"}
     :on-click (file-picker
                #js ["application/pdf"]
                #(doseq [blob (js->clj % :keywordize-keys true)]
                   (ws/send! [::deposit-link
                              {:url (:url blob)
                               :content-type "application/pdf"
                               :filename (:filename blob)}]
                             (partial add-local-deposit app (notify/add-message :processing)))))
     :class "btn btn-block btn-success btn-lg"
     :data-dismiss "modal"}
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
   {:style {:margin-top "50px"}}
   (dom/span
    {:class "lead"}
    "Upload CrossRef XML to register or update DOI metadata. The XML "
    " should conform to either the "
    (dom/a {:href ""} "full deposit")
    " schema or the "
    (dom/a {:href ""} "resource deposit")
    " schema.")
   (dom/button
    {:type "button"
     :style {:margin-top "40px"}
     :on-click (file-picker
                #js ["application/xml" "text/xml"]
                #(doseq [blob (js->clj % :keywordize-keys true)]
                   (ws/send! [::deposit-link
                              {:url (:url blob)
                               :content-type "application/vnd.crossref.any+xml"
                               :filename (:filename blob)}]
                             (partial add-local-deposit app (notify/add-message :processing)))))
     :class "btn btn-block btn-success btn-lg"
     :data-dismiss "modal"}
    (util/icon :upload)
    " Upload XML")))

(defn upload-modal [app]
  (dom/div
   {:class "modal fade"
    :id "upload-modal"
    :tab-index "-1"
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
         :content (xml-upload app)}]))
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

(defn lookup-doi [app v lookup-chan]
  (om/update! app [:lookup :text] v)
  (put! lookup-chan v))

(defn summarize-citation-list [citations]
  (let [c (map
           #(if-not (:match %)
              %
              {:match {:DOI (get-in % [:match :DOI])}
       :text (:text %)
               :number (:number %)})
           citations)]
    (println c)
    c))

(defn citation-deposit-modal [app lookup-chan generate-chan]
  (let [citation-count (-> app (get-in [:deposit :citations]) count)
        matched-count (count
                       (filter #(not (nil? (:match %)))
                               (get-in app [:deposit :citations])))
        lookup-result (get-in app [:lookup :result])]
    (dom/div
     {:class "modal fade" :id "citation-deposit-modal" :tab-index "-1"
      :role "dialog" :aria-hidden "true"}
     (dom/div
      {:class "modal-dialog"}
      (dom/div
       {:class "modal-content"}
       (dom/div
        {:class "modal-header"}
        (dom/button {:type "button" :class "close" :data-dismiss "modal"}
                    (dom/span {:aria-hidden "true"} (util/icon :remove))
                    (dom/span {:class "sr-only"} "Close"))
        (dom/h4 {:class "modal-title"} "Create a citations deposit"))
       (dom/div
        {:class "modal-body"}
        (dom/p
         "Deposit these "
         (dom/strong citation-count)
         " "
         (dom/strong (str "(" matched-count " matched)"))
         " citations as the citation list for an existing DOI")
        (dom/form
         {:role "form" :style {:margin-top "2em"}}
         (dom/div
          {:class "form-group"}
          (dom/input {:type "text" :class "form-control"
                      :id "citation-deposit-doi" :placeholder "Enter DOI..."
                      :value (get-in app [:lookup :text])
                      :on-change #(lookup-doi app (.-value (.-target %)) lookup-chan)}))
         (dom/div
          {:class "form-group"}
          (util/in-panel
           (cond (nil? lookup-result)
                 (util/loader)
                 (empty? lookup-result)
                 (dom/h3 {:class "text-danger"
                          :style {:margin-left "auto" :margin-right "auto"}}
                         (util/icon :ban-circle)
                         " DOI not found")
                 :else
                 (match-details lookup-result))
           :title "Resolves to"))))
       (dom/div
        {:class "modal-footer"}
        (dom/a
         (if (or (empty? lookup-result) (nil? lookup-result))
           {:type "button" :class "btn btn-success" :disabled true}
           {:type "button" :class "btn btn-success" :data-dismiss "modal"
            :on-click #(put! generate-chan
                             {:doi (get-in @app [:lookup :text])
                              :parent (get-in @app [:deposit :batch-id])
                              :citations (-> @app
                                             (get-in [:deposit :citations])
                                             summarize-citation-list)})})
         "Create deposit")))))))
      
(defcomponent citation-deposit [app owner]
  (init-state [_] {:lookup-chan (chan (sliding-buffer 1))
                   :generate-chan (chan)})
  (will-mount [_]
              (let [lookup-chan (om/get-state owner :lookup-chan)
                    generate-chan (om/get-state owner :generate-chan)]
                (go-loop [text (<! lookup-chan)]
                  (send-and-update!
                   [::lookup {:text text}]
                   app
                   [:lookup :result])
                  (recur (<! lookup-chan)))
                (go-loop [citation-deposit (<! generate-chan)]
                  (let [nid (notify/add-message :creating)]
                    (ws/send! [::generate-deposit citation-deposit]
                              (fn [_] (notify/finish-message nid)))
                    (recur (<! generate-chan))))))
  (render-state [_ {:keys [lookup-chan generate-chan]}]
                (citation-deposit-modal app lookup-chan generate-chan)))
   
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

(when-let [e (.getElementById js/document "citation-deposit")]
  (om/root citation-deposit
           page-state
           {:target e}))













