(defproject crossref/depositor "0.1.0-SNAPSHOT"
  :description "Deposit XML or PDFs with CrossRef"
  :url "http://www.github.com/CrossRef/depositor"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj" "src/cljs" "src/cljx"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2356"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/data.xml "0.0.8"]
                 ;[com.congitect/transit-clj "0.8.259"]
                 [com.cognitect/transit-cljs "0.8.188"]
                 [com.taoensso/sente "1.2.0"]
                 [com.cemerick/friend "0.2.1"]
                 [ring/ring-defaults "0.1.2"]
                 [compojure "1.2.0"]
                 [http-kit "2.1.19"]
                 [om "0.7.3"]
                 [prismatic/om-tools "0.3.2"]
                 [hickory "0.5.4"]
                 [environ "1.0.0"]
                 [hiccup "1.0.5"]
                 [com.andrewmcveigh/cljs-time "0.2.1"]
                 [clj-time "0.8.0"]
                 [javax.servlet/servlet-api "2.5"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-environ "1.0.0"]
            [com.cemerick/austin "0.1.5"]]

  :main depositor.server

  :profiles {:dev {:env {:server-port 3000
                         :server-threads 2
                         :server-queue-size 1000
                         :browser-repl true
                         :api "https://api.crossref.org"
                         :main-js "/js/main.dev.js"}}

             :production {:env {:server-port 3000
                                :server-threads 100
                                :server-queue-size 40000
                                :browser-repl false
                                :api "https://api.crossref.org"
                                :main-js "/js/main.js"}}}

  :cljsbuild {:builds {:production
                       {:source-paths ["src/cljs" "src/cljx"]
                        :compiler {:output-to "resources/public/js/main.js"}
                        :optimizations :advanced
                        :pretty-print false}
                        ;:preamble ["resources/jquery-1.11.1.min.js"
                        ;           "resources/bootstrap.min.js"]}
                       :dev
                       {:source-paths ["src/cljs" "src/cljx"]
                        :compiler {:output-to "resources/public/js/main.dev.js"}
                        :optimizations :whitespace
                        :pretty-print true}}})
                        ;:preamble ["resources/jquery-1.11.1.min.js"
                        ;           "resources/bootstrap.min.js"]}}})
            
                        
             
             

            
            

                 
