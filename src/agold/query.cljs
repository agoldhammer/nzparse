(ns query
  (:require [cljs-http.client :as http]
            [cljs.core.async :as a]
            ["xmlhttprequest" :refer [XMLHttpRequest]]))

;; for this hack, needed to make cljs-http work properly
;; see http://www.jimlynchcodes.com/blog/solved-xmlhttprequest-is-not-defined-with-clojurescript-coreasync

#_(set! js/XMLHttpRequest (nodejs/require "xhr2"))

;; this eliminates the annoying message from xhr2, which is no longer required
(set! js/XMLHttpRequest XMLHttpRequest)


(def base-url "http://localhost:5000")

(defn make-url
  "make url by concatenating args"
  [& args]
  (apply str base-url args))

(defn fetch-endpoint
  "fetch data from endpoint"
  [endpoint query-param-map]
  (a/go
    (let [response (a/<! (http/get (make-url endpoint)
                                   {:with-credentials? false
                                    :query-params query-param-map}))]
      (println (:body response))))
  :fetched)

(comment (make-url "/json/cats")
         (fetch-endpoint "/json/cats" {})
         (fetch-endpoint "/json/qry" {:data "'Woerth Sarkozy' -d 3"})
         (fetch-endpoint "/json/qry" {:data "Macron -s 2022-02-12T12:30:00"}))



