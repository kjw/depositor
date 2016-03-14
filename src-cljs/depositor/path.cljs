(ns depositor.path
  (:require [clojure.string :as string]))

(def site-prefix
  (.-className (.getElementById js/document "context")))

;; Annoying work around for annoying clojurescript regex bugs
;; (defn take-off-slashes [s]
;;   (cond (and (string/starts-with? "/")
;;              (string/ends-with? "/"))
;;         (-> s drop-last rest str)
;;         (string/starts-with? "/")
;;         (-> s rest str)
;;         (string/ends-with? "/")
;;         (-> s drop-last str)
;;         :else
;;         s))

(defn path-to [& ps]
  (-> site-prefix
      (str "/" (string/join "/" ps))
      (string/replace #"/+" "/")
      (string/replace #"/\Z" "")))


