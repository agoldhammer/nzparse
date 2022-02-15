(ns query
  (:require [cljs-http.client :as http]
            [cljs.core.async :as a]
            [cljs.pprint :as pp]
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

(defn get-endpoint
  "fetch data from endpoint"
  [endpoint query-param-map]
  (a/go
    (let [response (a/<! (http/get (make-url endpoint)
                                   {:with-credentials? false
                                    :query-params query-param-map}))]
      (println (:body response))))
  :fetched)

(defn post-endpoint
  "fetch data from endpoint"
  [endpoint query-param-map]
  (a/go
    (let [response (a/<! (http/post (make-url endpoint)
                                    {:with-credentials? false
                                     :json-params query-param-map}))
          status (:status response)]
      (if (= status 200)
        (let [body (:body response)
              err (:error body)]
          (if (= err 0)
            (pp/pprint (:statuses body))
            (println "fetch error:" err)))
        (println "network error"))))
  :fetched)

(comment (make-url "/json/cats")
         (get-endpoint "/json/cats" {})
         (get-endpoint "/json/qry" {:data "'Woerth Sarkozy' -d 3"})
         (get-endpoint "/json/qry" {:data "Macron -s 2022-02-12T12:30:00"})
         (post-endpoint "/json/xqry" {:words ["Macron", "Scholz"] :start "2022-02-14"
                                      :end "2022-02-15"}))





