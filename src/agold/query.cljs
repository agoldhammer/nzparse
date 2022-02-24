(ns query
  (:require [cljs-http.client :as http]
            [cljs.core.async :as a]
            [cljs.core.async.interop :refer-macros [<p!]]
            #_[cljs.pprint :as pp]
            [process]
            ["fs" :as fs]
            ["http" :as nhttp]
            ["open" :as open]
            ["xmlhttprequest" :refer [XMLHttpRequest]]))

(process/on "uncaughtException", (fn [err origin]
                                   (println "Uncaught Exception" err origin)))

(defn slurp
  "read file into string"
  [fname]
  (-> (fs/readFileSync fname)
      (.toString)))

(def out (atom ""))

(defn svr-resp-fn
  [req res]
  (println (.-url req))
  (condp = (.-url req)
    "/fetcher.html" (do
                      (.write res (slurp "resources/fetcher.html"))
                      (.end res))
    "/spec.json" (do
                   (.writeHead res 200 (clj->js {"Content-Type" "application/json"}))
                   (.end res @out))
    (.writeHead res 404 (clj->js {"Content-Type" "text/html"}))))


;; ! for this hack, needed to make cljs-http work properly
;; ? see http://www.jimlynchcodes.com/blog/solved-xmlhttprequest-is-not-defined-with-clojurescript-coreasync

;; ? (set! js/XMLHttpRequest (nodejs/require "xhr2"))

;; ? this eliminates the annoying message from xhr2, which is no longer required
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

(defn result->json
  "convert raw xgraph fetch to json, dissociating :time"
  [result]
  (.stringify js/JSON (clj->js (dissoc (:result result) :time))))

(defn vega-fetch-and-open
  "ok-fn for vega fetch"
  [result]
  (reset! out (result->json result))
  (a/go
    (try
      (<p! (open "http://127.0.0.1:2626/fetcher.html"))
      (catch js/Error err (js/console.log (ex-cause err))))))

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

(comment
  @out
  (get-endpoint "/json/cats" {})
  (def svr (nhttp/createServer svr-resp-fn))
  (.listen svr 2626 "127.0.0.1")
  (.close svr)

  (def left
    {:endpoint "/json/xgraph"
     :json-params {:subqueries [["Melenchon"] ["Hidalgo"] ["Taubira"] ["Jadot"]
                                ["Roussel"]] :start "2021-09-01"
                   :title "Candidates of the Left"
                   :interval "30d" :n 6}
     :ok-fn vega-fetch-and-open
     :err-fn println})

  (def right
    {:endpoint "/json/xgraph"
     :json-params {:subqueries [["Pecresse"] ["Zemmour"] ["Pen Marine"] ["Ciotti"]
                                ["Bertrand"] ["Barnier"]] :start "2021-09-01"
                   :title "Candidates of the Right"
                   :interval "30d" :n 6}
     :ok-fn vega-fetch-and-open
     :err-fn println})

  (def Ukraine
    {:endpoint "/json/xgraph"
     :json-params {:subqueries [["Ukraine" "Ucraina"] ["Putin" "Poutine" "Lavrov" "Lavrow"] ["Zelensky"]
                                ["Biden" "Blinken"]]
                   :start "2021-09-01"
                   :title "Ukraine"
                   :interval "30d" :n 6}
     :ok-fn vega-fetch-and-open
     :err-fn println})

  (def Germany
    {:endpoint "/json/xgraph"
     :json-params {:subqueries [["Scholz"] ["Baerbock"] ["Habeck"] ["Merkel"]
                                ["Lindner"] ["Lauterbach"] ["Merz"] ["Söder"]]
                   :title "German Leadership"
                   :start "2021-09-01"
                   :interval "30d" :n 6}
     :ok-fn vega-fetch-and-open
     :err-fn println})

  (def center
    {:endpoint "/json/xgraph"
     :json-params {:subqueries [["Macron"] ["Castex"] ["Philippe"]
                                ["Woerth"] ["Maire"]]
                   :start "2021-09-01"
                   :title "Macron and Others"
                   :interval "30d" :n 6}
     :ok-fn vega-fetch-and-open
     :err-fn println})

  (def companies
    {:endpoint "/json/xgraph"
     :json-params {:subqueries [["Microsoft"] ["Apple"] ["Facebook Meta"] ["Google" "Alphabet"]
                                ["Amazon"] ["Siemens"] ["Volkswagen"]]
                   :start "2021-09-01"
                   :title "Companies"
                   :interval "30d" :n 6}
     :ok-fn vega-fetch-and-open
     :err-fn println})

  (def unions
    {:endpoint "/json/xgraph"
     :json-params {:subqueries [["Metall"] ["CGT"] ["CFDT"] ["Ouvriere"]
                                ["CGIL"]]
                   :start "2021-09-01"
                   :title "Unions"
                   :interval "30d" :n 6}
     :ok-fn vega-fetch-and-open
     :err-fn println})

  (def Italy
    {:endpoint "/json/xgraph"
     :json-params {:subqueries [["Draghi"] ["Mattarella"] ["Salvini"] ["Meloni"]
                                ["Conte"] ["Maio"] ["Letta"] ["Renzi"] ["Berlusconi"]]
                   :start "2021-09-01"
                   :title "Italian Leadership"
                   :interval "30d" :n 6}
     :ok-fn vega-fetch-and-open
     :err-fn println})

  (post-endpoint-x left)
  (post-endpoint-x Ukraine)
  (post-endpoint-x right)
  (post-endpoint-x center)
  (post-endpoint-x Germany)
  (post-endpoint-x companies)
  (post-endpoint-x unions)
  (post-endpoint-x Italy)
  (a/go
    (try
      (<p! (open "http:127.0.0.1:2626/fetcher.html"))
      (catch js/Error err (js/console.log (ex-cause err))))))





