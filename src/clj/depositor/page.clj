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
            [environ.core :refer [env]]))

(defn repl-js-script []
  (-> (str "<script>"
           (if (env :browser-repl) (browser-connected-repl-js) "")
           "</script>")
      hc/parse-fragment
      first
      hc/as-hickory))

(defn main-js-script []
  (-> (str "<script src=\"" (env :main-js) "\"></script>")
      hc/parse-fragment
      first
      hc/as-hickory))

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
       (#(zip/append-child % (-> content hc/parse-fragment first hc/as-hickory)))
       (#(zip/append-child % (main-js-script)))
       (#(zip/append-child % (repl-js-script)))
       zip/root
       hr/hickory-to-html))

