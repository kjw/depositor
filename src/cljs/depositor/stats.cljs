(ns depositor.stats
  (:require-macros [cljs.core.async.macros :refer (go go-loop)])
  (:require [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [om.core :as om]
            [depositor.ws :as ws :refer (send-and-update!)]
            [depositor.util :as util]
            [taoensso.sente :refer (cb-success?)]
            [cljs.core.async :refer (<! >! put! chan)]))

(declare update-for-member)

(defn member-chooser [app members]
  (cond
   (-> members count (= 1))
   (dom/span {:class "small"}
             (dom/span {:class "glyphicon glyphicon-user"})
             "Showing:"
             (-> members first :name dom/b))
   
    (-> members count (> 1))
    (dom/form
     {:class "form-inline"}
     (dom/div 
      {:class "form-group"}
      (dom/label {:class "small"
                  :for "member"
                  :style {:padding-right "5px"}}
                 (dom/span {:class "glyphicon glyphicon-user"})
                 "Showing:")
      (dom/select 
       {:class "form-control"
        :name "member"
        :on-change #(update-for-member app :id (-> (.. % -target -value) js/parseInt))}
       (for [member members]
         (dom/option {:value (:id member)} (:name member))))))))

(defn status-row [title description current-count backfile-count
                  & {:keys [percent] :or {percent false}}]
  (let [current-s (if percent 
                    (util/format-percent current-count) 
                    (util/format-integer current-count))
        backfile-s (if percent
                     (util/format-percent backfile-count)
                     (util/format-integer backfile-count))
        current-clzz (str "lead" (when (zero? current-count) 
                               " text-danger"))
        backfile-clzz (str "lead" (when (zero? backfile-count)
                                " text-danger"))]
    (dom/div {:class "row" :style {:border-top "1px solid #ddd"
                                   :height "5em"}}
             (dom/div {:class "col-md-8"}
                      (dom/h5 title)
                      (dom/p description))
             (dom/div {:class "col-md-2" :style {:margin-top "1.2em"}}
                      (dom/span {:class current-clzz} current-s))
             (dom/div {:class "col-md-2" :style {:margin-top "1.2em"}}
                      (dom/span {:class backfile-clzz} backfile-s)))))

(defn status-table [status]
  (dom/div
   {:class "panel panel-default"}
   (dom/div
    {:class "panel-body"}
    (dom/h4 {:class "panel-title"} "Overview")
    (dom/div {:class "row"}
             (dom/div {:class "col-md-2 col-md-offset-8"}
                      (dom/span {:class "small"} "current"))
             (dom/div {:class "col-md-2"}
                      (dom/span {:class "small"} "backfile")))
    (status-row "Registered"
                "DOIs registered with CrossRef"
                (get-in status [:counts :current-dois])
                (get-in status [:counts :backfile-dois]))
    (status-row "Funding Information"
                "DOIs with metadata linking them to funding organiations"
                (get-in status [:coverage :funders-current])
                (get-in status [:coverage :funders-backfile])
                :percent true)
    (status-row "Award Numbers"
                "Metadata records with at least one funder award number"
                (get-in status [:coverage :award-numbers-current])
                (get-in status [:coverage :award-numbers-backfile])
                :percent true)
    (status-row "License URIs"
                "Records with at least one license URI"
                (get-in status [:coverage :licenses-current])
                (get-in status [:coverage :licenses-backfile])
                :percent true)
    (status-row "Reference Lists"
                "DOI metadata records with a list of citations"
                (get-in status [:coverage :references-current])
                (get-in status [:coverage :references-backfile])
                :percent true)
    (status-row "Full-text Links"
                "DOIs with full-text links for text and data mining"
                (get-in status [:coverage :resource-links-current])
                (get-in status [:coverage :resource-links-backfile])
                :percent true)
    (status-row "ORCIDs"
                "DOIs linked to ORCIDs in CrossRef metadata"
                (get-in status [:coverage :orcids-current])
                (get-in status [:coverage :orcids-backfile])
                :percent true)
    (status-row "Crossmark Update Policies"
                "Coverage of CrossMark update policies"
                (get-in status [:coverage :update-policies-current])
                (get-in status [:coverage :update-policies-backfile])
                :percent true))))
              
(defn top-licenses [reachability licenses]
  (dom/div 
   {:class "panel panel-default"}
   (dom/div 
    {:class "panel-body"}
    (dom/h4 {:class "panel-title"} "Top Licenses")
    (dom/p 
     "These are the most frequently used license URIs in the member's DOI metadata.")
    (if-not licenses
      (util/loader)
      (dom/table
       {:class "table fadein"}
       (dom/thead
        (dom/th "License URI")
        (dom/th 
         "Reachable "
         (dom/a {:data-toggle "tooltip" 
                 :title "Does the license URI resolve to a valid web page?"
                 :href "#"}
          (dom/span {:class "glyphicon glyphicon-info-sign text-info"})))
        (dom/th "Registered DOIs"))
       (dom/tbody
        (for [license licenses]
          (dom/tr
           (dom/td
            (dom/a {:href (-> license first)} (-> license first)))
           (condp = (-> license first reachability)
             nil (dom/td 
                  {:class "text-warning"} 
                  (dom/span {:class "glyphicon glyphicon-question-sign"}))
             true (dom/td 
                   {:class "text-success"} 
                   (dom/span {:class "glyphicon glyphicon-ok"}))
             false (dom/td 
                    {:class "text-danger"} 
                    (dom/span {:class "glyphicon glyphicon-remove"})))
           (dom/td (second license))))))))))

(defn top-funders [funders]
  (dom/div 
   {:class "panel panel-default"}
   (dom/div 
    {:class "panel-body"}
    (dom/h4 {:class "panel-title"} "Top Funders")
    (dom/p "These are the most referred to funder names in the member's DOI metadata.")
    (if-not funders
      (util/loader)
      (dom/table 
       {:class "table fadein"}
       (dom/thead
        (dom/th "Funder Name")
        (dom/th "Registered DOIs"))
       (dom/tbody
        (for [funder funders]
          (dom/tr
           (dom/td (-> funder first name))
           (dom/td (second funder))))))))))

(defn check-license [app [license-uri count]]
  (ws/send! [::license-check license-uri]
            (fn [reply]
              (when (cb-success? reply)
                (om/transact! app 
                              :member-license-checks 
                              #(assoc % license-uri reply))))))

(defn update-for-member [app & {:keys [id] :or {id :none}}]
  (om/update! app :member-status nil)
  (om/update! app :member-licenses nil)
  (om/update! app :member-funders nil)
  (om/update! app :member-license-checks {})
  (let [data (if (= :none id)
               {}
               {:id id})]
    (send-and-update! [::member-status data] app :member-status)
    (send-and-update! [::license-breakdown data] app :member-licenses)
    (send-and-update! [::funding-breakdown data] app :member-funders)))

(defcomponent statistics [app owner]
  (will-mount [_]
              (send-and-update! [::members {}] app :members)
              (update-for-member app))
  (render-state [_ _]
                (dom/div
                 (dom/div 
                  {:class "well well-sm"}
                  (dom/div
                   {:class "row"}
                   (dom/div
                    {:class "col-md-6"}
                    (member-chooser app (:members app)))
                   (dom/div
                    {:class "col-md-6"}
                    (dom/span
                     {:class "pull-right small"}
                     (dom/span {:class "glyphicon glyphicon-time"})
                     " Last updated: " 
                     (dom/b (-> app
                                (get-in [:member-status 
                                         :last-status-check-time])
                                util/friendly-date))))))
                  (if (:member-status app)
                    (dom/div {:class "fadein"}
                     (-> app :member-status status-table)
                     (-> app :member-licenses ((partial top-licenses 
                                                        (:member-license-checks app))))
                     (-> app :member-funders top-funders))
                    (util/loader)))))

(def page-state
  (atom {:members []
         :member-status nil
         :member-licenses nil
         :member-license-checks {}
         :member-funders nil}))

(when-let [e (.getElementById js/document "statistics")]
  (om/root statistics
           page-state
           {:target e}))
