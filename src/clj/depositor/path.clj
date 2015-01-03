(ns depositor.path
  (:require [clojure.string :as string]
            [environ.core :refer [env]]))

(def site-prefix (or (env :server-path) "/"))

(defn path-to [& ps]
  (-> site-prefix
      (str "/" (string/join "/" ps))
      (string/replace #"/+" "/")
      (string/replace #"/\Z" "")))

(defn img [name] (path-to "img" name))
(defn css [name] (path-to "css" name))
(defn javascript [name] (path-to "js" name))
    
