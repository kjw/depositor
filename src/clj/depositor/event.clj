(ns depositor.event
  (:require [compojure.core :refer [defroutes GET POST]]
            [taoensso.sente :refer [make-channel-socket! start-chsk-router!]]))

(defmulti handle-socket-event :id)

(defmethod handle-socket-event :chsk/uidport-open [_] nil)
(defmethod handle-socket-event :chsk/uidport-close [_] nil)
(defmethod handle-socket-event :chsk/ws-ping [_] nil)

(def socket (atom nil))

(def router (atom nil))

(defroutes socket-routes
  (POST "/" [] (:socket-post-handler @socket))
  (GET "/" [] (:socket-get-handler @socket)))

(defn start []
  (let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
                connected-uids]}
        (make-channel-socket! {:user-id-fn #(get-in % [:session
                                                       :cemerick.friend/identity
                                                       :current])})]
    (reset! socket
            {:socket-post-handler ajax-post-fn
             :socket-get-handler ajax-get-or-ws-handshake-fn
             :socket-chan ch-recv
             :send! send-fn
             :clients connected-uids})
    (reset! router (start-chsk-router! ch-recv handle-socket-event))))

(defn stop []
  (when-let [stop-fn @router]
    (stop-fn)))



