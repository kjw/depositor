(ns depositor.server
  "Serve pages and handle web socket connections."
  (:require [compojure.core :refer [GET POST defroutes routes context]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [compojure.handler :refer [site]]
            [org.httpkit.server :refer [run-server]]
            [environ.core :refer [env]]
            [cemerick.friend :refer [wrap-authorize authenticate]]
            [cemerick.friend.workflows :refer [interactive-form]]
            [depositor.auth :refer [crossref-credentials authorization-routes]]
            [depositor.landing :refer [landing-routes]]
            [depositor.stats :refer [stats-routes]]
            [depositor.deposit :refer [deposit-routes]]
            [depositor.event :refer [socket-routes] :as event]
            [depositor.permission :refer [permission-routes]]))

(def server (atom nil))

(def all-routes
  (-> (routes
       (context "/socket" [] (wrap-authorize socket-routes #{:user}))
       (context "/deposits" [] (wrap-authorize deposit-routes #{:user}))
       (context "/permissions" [] (wrap-authorize permission-routes #{:user}))
       (context "/statistics" [] (wrap-authorize stats-routes #{:user}))
       authorization-routes
       landing-routes)
      (authenticate {:credential-fn crossref-credentials
                     :workflows [(interactive-form :login-uri "/login")]})
      (wrap-defaults site-defaults)))

(defn start []
  (event/start)
  (reset! server (run-server #'all-routes
                             {:port (env :server-port)
                              :thread (env :server-threads)
                              :queue-size (env :server-queue-size)})))

(defn stop [] 
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main []
  (event/start)
  (run-server #'all-routes
              {:port (env :server-port)
               :thread (env :server-threads)
               :queue-size (env :server-queue-size)
               :join? false}))
