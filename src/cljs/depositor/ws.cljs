(ns depositor.ws
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! put! close! chan]]
            [cognitect.transit :as t]))

(def socket (atom nil))

(defn create []
  (let [incoming (chan)
        outgoing (chan)]
    {:host "localhost"
     :port 3000
     :path "/socket"
     :incoming incoming
     :outgoing outgoing}))

(defn send-data [data]
  (put! (:outgoing @socket) data)) ;(t/write data)))

(defn on-data [data]
  (.log js.console data)); (t/read data)))

(defn start [ws]
  (let [url (str "ws://" (:host ws) ":" (:port ws) (:path ws))
        web-socket (js/WebSocket. url)]
    (.onmessage socket (fn [e] (put! (:incoming ws) (get e "data"))))
    (go (loop []
          (on-data (<! (:incoming ws)))
          (recur)))
    (go (loop []
          (.send socket (<! (:outgoing ws)))
          (recur)))
    (reset! socket (assoc ws :socket web-socket))))

(defn stop []
  (when @socket
    (close! (:incoming @socket))
    (close! (:outgoing @socket))
    (reset! socket nil)))
    
