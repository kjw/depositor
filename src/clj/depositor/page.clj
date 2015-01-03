(ns depositor.page
  "Generates the index page either with or without a browser repl
   connection."
  (:require [clojure.java.io :as io]
            [cemerick.austin.repls :refer [browser-connected-repl-js]]
            [clojure.zip :as zip]
            [hickory.core :as hc]
            [hickory.render :as hr]
            [hickory.select :as hs]
            [hickory.zip :as hz]
            [environ.core :refer [env]]
            [depositor.path :refer [site-prefix javascript css]]))

(def modernizer "modernizr-2.6.2-respond-1.1.0.min.js")
(def jquery "jquery-1.11.1.min.js")
(def bootstrap "bootstrap.min.js")

(def react "//fb.me/react-0.9.0.js")
(def filepicker "//api.filepicker.io/v1/filepicker.js")

(def bootstrap-css "bootstrap.min.css")
(def main-css "main.css")

(defn snippet [s] (-> s hc/parse-fragment first hc/as-hickory))

(defn repl-js-script []
  (snippet
   (str "<script>"
        (if (env :browser-repl) (browser-connected-repl-js) "")
        "</script>")))

(defn main-js-script []
  (snippet
   (str "<script src=\"" (javascript (env :main-js)) "\"></script>")))
   
(defn local-js-script [name]
  (snippet
   (str "<script src=\"" (javascript name) "\"></script>")))

(defn remote-js-script [url]
  (snippet
   (str "<script src=\"" url "\"></script>")))

(defn local-css [name]
  (snippet
   (str "<link rel=\"stylesheet\" href=\"" (css name) "\">")))

(defn context-element []
  (snippet
   (str "<div id=\"context\" class=\"" site-prefix "\"></div>")))

(defn original-page []
  (-> "index.html"
      io/resource
      slurp
      hc/parse
      hc/as-hickory))

(defn page [& {:keys [content] :or {content "<div></div>"}}]
  (->> (original-page)
       hz/hickory-zip
       (hs/select-next-loc (hs/tag :body))

       (#(zip/append-child % (context-element)))
       
       (#(zip/append-child % (-> content hc/parse-fragment first hc/as-hickory)))
       
       ;;hs/root
       ;;(hs/select-next-loc (hs/tag :head))
       (#(zip/append-child % (local-css bootstrap-css)))
       (#(zip/append-child % (local-css main-css)))
       (#(zip/append-child % (local-js-script modernizer)))
       (#(zip/append-child % (local-js-script jquery)))
       (#(zip/append-child % (local-js-script bootstrap)))
       (#(zip/append-child % (remote-js-script react)))
       (#(zip/append-child % (remote-js-script filepicker)))

       (#(zip/append-child % (main-js-script)))
       (#(zip/append-child % (repl-js-script)))
       zip/root
       hr/hickory-to-html))

