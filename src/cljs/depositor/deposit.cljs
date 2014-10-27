(ns depositor.deposit
  (:require [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om.core :as om]
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
  (atom {:deposits nil}))

;; (defcomponent deposit [deposit owner]
;;   ())

;; status - completed failed submitted
;; time of submission
;; test?
;; content type
;; length
;; (submission history)
;; (submission errors)
;; (dois)

(defcomponent deposit-item [deposit owner]
  (render-state [_ {:keys []}]
                (dom/li {:class "list-group-item"}
                        (dom/p "i"))))

(defcomponent deposit-list [app owner]
  (will-mount [_] (send-and-update! [::deposits {:rows 10}] app :deposits))
  (render-state [_ {:keys []}]
                (if (nil? (:deposits app))
                  (util/loader)
                  (dom/div
                   {:class "fadein"}
                   (dom/ul {:class "list-group"}
                           (om/build-all deposit-item (get-in app [:deposits :items])))
                   (util/paginate-list (:deposits app))))))

(when-let [e (.getElementById js/document "deposits")]
  (om/root deposit-list
           page-state
           {:target e}))
