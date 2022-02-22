(ns query
  (:require [cljs-http.client :as http]
            [cljs.core.async :as a]
            [cljs.pprint :as pp]
            [process]
            ["fs" :as fs]
            ["xmlhttprequest" :refer [XMLHttpRequest]]))

(process/on "uncaughtException", (fn [err origin]
                              (println "Uncaught Exception" err origin)))

;; for this hack, needed to make cljs-http work properly
;; see http://www.jimlynchcodes.com/blog/solved-xmlhttprequest-is-not-defined-with-clojurescript-coreasync

#_(set! js/XMLHttpRequest (nodejs/require "xhr2"))

;; this eliminates the annoying message from xhr2, which is no longer required
(set! js/XMLHttpRequest XMLHttpRequest)

(defn slurp
  "read file into string"
  [fname]
  (-> (fs/readFileSync fname)
      (.toString)))


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

(def out (atom ""))
(defn graphout
  [result]
  #_(reset! out (clj->js (stringify-keys (:result result))))
  (println (.stringify js/JSON (clj->js (dissoc (:result result) :time))))

  #_(reset! out result))

(def graph-route {
                  :endpoint "/json/xgraph"
                  :json-params {:subqueries [["Pecresse"] ["Zemmour"] ["Pen"]] :start "2022-02-11"
                             :interval "1d" :n 10}
                  :ok-fn graphout
                  :err-fn println
})

(defn post-endpoint-x
  "fetch data from route map"
  [route]
  (let
   [{:keys [endpoint json-params ok-fn err-fn]} route]
    (a/go
      (let [response (a/<! (http/post (make-url endpoint)
                                      {:with-credentials? false
                                       :json-params json-params}))
            status (:status response)]
        (if (= status 200)
          (let [body (:body response)
                err (:error body)]
            (if (= err 0)
              (ok-fn body)
              (err-fn "fetch error:" err)))
          (err-fn "network error")))))
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
            (pp/pprint body)
            (println "fetch error:" err)))
        (println "network error"))))
  :fetched)

(comment (make-url "/json/cats")
         (get-endpoint "/json/cats" {})
         (get-endpoint "/json/qry" {:data "'Woerth Sarkozy' -d 3"})
         (get-endpoint "/json/qry" {:data "Macron -s 2022-02-12T12:30:00"})
         (post-endpoint "/json/xqry" {:words ["Macron", "Scholz"] :start "2022-02-14"
                                      :end "2022-02-15"})
         (post-endpoint "/json/xcount" {:words ["Macron", "Scholz"] :start "2022-02-15"
                                        :end "2022-02-16"})
         (post-endpoint "/json/xcount" {:words ["Putin", "Poutine", "Ukraine", "Zelensky"] :start "2022-02-15"
                                        :end "2022-02-16"})
         (post-endpoint "/json/xcount" {:words ["Scholz"] :start "2022-02-15"
                                        :end "2022-02-16"})
         (post-endpoint "/json/xgraph" {:subqueries [["Scholz"]["Macron"]["Putin"]] :start "2022-02-15"
                                        :interval "1d" :n 7})
         (post-endpoint-x graph-route)
         @out
         (println (.stringify js/JSON (clj->js (dissoc (:result @out) :time))))
         (def index-templ (slurp "resources/index.templ"))
         (.replace index-templ "VGJSON" "xyz")
         )





