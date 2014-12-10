(ns depositor.ws
  (:require-macros [cljs.core.async.macros :refer (go go-loop)])
  (:require [cljs.core.async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [om.core :as om]
            [cognitect.transit :as t]))

(enable-console-print!)

(declare start-event-sender!)

(defmulti handle-socket-event :id)

(defmethod handle-socket-event :chsk/state 
  [{:as msg :keys [?data]}]
  (when (:first-open? ?data)
    (start-event-sender!)))

(let [{:keys [chsk ch-recv send-fn state]} 
        (sente/make-channel-socket! "/socket" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state)
  (sente/start-chsk-router! ch-recv handle-socket-event))

(def send-ch (chan 100))

(defn send! [event reply-fn]
  (put! send-ch {:event event :reply-fn reply-fn}))

(defn send-and-update! [event app-state k]
  (send! event
         (fn [reply]
           (when (cb-success? reply)
             (om/update! app-state k reply)))))

(defn start-event-sender! []
  (go-loop [{:keys [event reply-fn]} (<! send-ch)]
    (println event)
    (chsk-send! event 30000 reply-fn)
    (recur (<! send-ch))))

