(ns query
  (:require [cljs-http.client :as http]
            [cljs.core.async :as a]
            [cljs.core.async.interop :refer-macros [<p!]]
            [cljs.pprint :as pp]
            [process]
            ["fs" :as fs]
            ["http" :as nhttp]
            ["open" :as open]
            ["xmlhttprequest" :refer [XMLHttpRequest]]))

(process/on "uncaughtException", (fn [err origin]
                              (println "Uncaught Exception" err origin)))

(declare out)
(declare slurp)

(defn svr-resp-fn
  [req res]
  (println (.-url req))
  #_(.writeHead res 200 (clj->js {"Content-Type" "application/json"}))
  #_(.end res "Hello world!")
  (condp = (.-url req)
    "/fetcher.html" (do
                     (.write res (slurp "resources/fetcher.html"))
                     (.end res))
    "/spec.json" (do
                   (.writeHead res 200 (clj->js {"Content-Type" "application/json"}))
                   #_(.end res "Hi there")
                   (.end res @out))
    (.writeHead res 404 (clj->js {"Content-Type" "text/html"}))))


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

#_(defn spit
  "write string to file"
  [fname text]
  (fs/writeFileSync fname text))


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

(defn result->json
  "convert raw xgraph fetch to json, dissociating :time"
  [result]
  (.stringify js/JSON (clj->js (dissoc (:result result) :time))))

(defn graphout
  [result]
  #_(reset! out (clj->js (stringify-keys (:result result))))
  #_(println (.stringify js/JSON (clj->js (dissoc (:result result) :time))))
  (println (result->json result))

  #_(reset! out result))

(defn vega-fetch-and-open
  "ok-fn for vega fetch"
  [result]
  (reset! out (result->json result))
  (a/go
   (try
     (<p! (open "http://127.0.0.1:2626/fetcher.html"))
     (catch js/Error err (js/console.log (ex-cause err)) ))))

(def graph-route {:endpoint "/json/xgraph"
                  :json-params {:subqueries [["Pecresse"] ["Zemmour"] ["Pen"]] :start "2022-02-11"
                                :interval "1d" :n 10}
                  :ok-fn graphout
                  :err-fn println})

(def vega-route
  {:endpoint "/json/xgraph"
   :json-params {:subqueries [["Pecresse"] ["Zemmour"] ["Pen"]] :start "2021-09-01"
                 :interval "21d" :n 8}
   :ok-fn vega-fetch-and-open
   :err-fn println})

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
         (post-endpoint "/json/xgraph" {:subqueries [["Scholz"] ["Macron"] ["Putin"]] :start "2022-02-15"
                                        :interval "1d" :n 7})
         (post-endpoint-x graph-route)
         @out
         (println (.stringify js/JSON (clj->js (dissoc (:result @out) :time))))
         (post-endpoint-x vega-route)

         (def svr (nhttp/createServer svr-resp-fn))
         (.listen svr 2626 "127.0.0.1")
         (.close svr)

         (def left
           {:endpoint "/json/xgraph"
            :json-params {:subqueries [["Melenchon"] ["Hidalgo"] ["Taubira"] ["Jadot"]
                                       ["Roussel"]] :start "2021-09-01"
                          :interval "30d" :n 6}
            :ok-fn vega-fetch-and-open
            :err-fn println})

         (def right
           {:endpoint "/json/xgraph"
            :json-params {:subqueries [["Pecresse"] ["Zemmour"] ["Pen Marine"] ["Ciotti"]
                                       ["Bertrand"]] :start "2021-09-01"
                          :interval "30d" :n 6}
            :ok-fn vega-fetch-and-open
            :err-fn println})

         (def Ukraine
           {:endpoint "/json/xgraph"
            :json-params {:subqueries [["Ukraine" "Ucraina"] ["Putin Poutine"] ["Zelensky"]
                                       ["Donbas" "Donetsk"]]
                          :start "2021-09-01"
                          :interval "30d" :n 6}
            :ok-fn vega-fetch-and-open
            :err-fn println})

         (post-endpoint-x left)
         (post-endpoint-x Ukraine)
         (post-endpoint-x right)

         (a/go
          (try
            (<p! (open "http:127.0.0.1:2626/fetcher.html"))
            (catch js/Error err (js/console.log (ex-cause err)))))


         )





