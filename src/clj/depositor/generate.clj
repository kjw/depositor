(ns depositor.generate
  "Generate citation deposit XML for an existing citation
   deposit."
  (:require [hiccup.core :as hiccup]))

(defn head []
  [:head
    [:doi_batch_id]
    [:depositor
     [:name "CrossRef Depositor"]
     [:email_address]]])

(defn citation-key [citation]
  (or (:number citation) (str (java.util.UUID/randomUUID))))

(defn citation [citation]
  [:citation {:key (citation-key citation)}
   [:unstructured_citation (:text citation)]
   (when (:match citation)
     [:doi (get-in citation [:match :DOI])])])

(defn citation-list [citations]
  [:citation_list (for [c citations] (citation c))])

(defn citation-deposit [doi citations]
  (-> [:doi_batch {:version "4.3.4"
                   :xmlns "http://www.crossref.org/doi_resources_schema/4.3.4"
                   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
                   :xsi:schemaLocation
                   (str "http://www.crossref.org/doi_resources_schema/4.3.4"
                        " http://www.crossref.org/schema/deposit/doi_resources4.3.4.xsd")}
       (head)
       [:body
        [:doi_citations
         [:doi doi]
         (citation-list citations)]]]
      hiccup/html))
      
