(ns depositor.path
  (:require [clojure.string :as string]))

(def site-prefix
  (.-className (.getElementById js/document "context")))

(defn path-to [& ps]
  (-> site-prefix
      (str "/" (string/join "/" ps))
      (string/replace #"/+" "/")
      (string/replace #"/\Z" "")))

