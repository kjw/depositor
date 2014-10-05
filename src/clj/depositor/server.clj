(ns depositor.server
  "Serve the index page and handle web socket connections."
  (:require [depositor.page :refer [page]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [site]]
            [org.httpkit.server :refer [run-server with-channel 
                                        on-close on-receive send!]]
            [environ.core :refer [env]]))

(def server (atom nil))

(def clients (atom {}))
      
(defn web-socket [req]
  (with-channel req channel
    (on-close channel (fn [status] (println "closed")))
    (on-receive channel (fn [data] (send! channel data)))))

(defroutes all-routes
  (resources "/")
  (GET "/socket" [] web-socket)
  (GET "/" [] (page)))

(defn start []
  (reset! server (run-server (site #'all-routes)
                             {:port (env :server-port)
                              :thread (env :server-threads)
                              :queue-size (env :server-queue-size)})))

(defn stop [] 
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))
