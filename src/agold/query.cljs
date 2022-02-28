(ns query
  (:require [cljs-http.client :as http]
            [cljs.core.async :as a]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.edn :as edn]
            #_[cljs.pprint :as pp]
            [process]
            ["path" :as path]
            ["fs" :as fs]
            ["http" :as nhttp]
            ["open" :as open]
            ["xmlhttprequest" :refer [XMLHttpRequest]]))

#_(process/on "uncaughtException", (fn [err origin]
                                     (println "Uncaught Exception" err origin)))

(def exit-chan (a/chan))

(defn path-to-fetcher
  "resolve path to fetcher"
  []
  (let [procdir (js* "__dirname")]
    (path/resolve procdir "../../resources/fetcher.html")))

(defn slurp
  "read file into string"
  [fname]
  (-> (fs/readFileSync fname)
      (.toString)))

(defn read-program
  "read a program file"
  [fpath]
  (edn/read-string (slurp fpath)))

(def out (atom ""))

(defn svr-resp-fn
  [^js req ^js res]
  #_(println (.-url req))
  (condp = (.-url req)
    "/fetcher.html" (do
                      (.write res (slurp (path-to-fetcher)))
                      (.end res))
    "/spec.json" (do
                   (.writeHead res 200 (clj->js {"Content-Type" "application/json"}))
                   (.end res @out))
    (.writeHead res 404 (clj->js {"Content-Type" "text/html"}))))


;; ! for this hack, needed to make cljs-http work properly, see:
;; http://www.jimlynchcodes.com/blog/solved-xmlhttprequest-is-not-defined-with-clojurescript-coreasync
;; ? (set! js/XMLHttpRequest (nodejs/require "xhr2"))
;; ? this eliminates the annoying message from xhr2, which is no longer required
(set! js/XMLHttpRequest XMLHttpRequest)
;; ------------------------------------

;; ? For local testing
#_(def base-url "http://localhost:5000")

(defn make-url
  "make url by concatenating args"
  [server endpoint]
  (str server endpoint))

;; not used in app
#_(defn get-endpoint
    "fetch data from endpoint"
    [server endpoint query-param-map]
    (a/go
      (let [response (a/<! (http/get (make-url server endpoint)
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
      (a/>! exit-chan :done)
      (catch js/Error err (js/console.log (ex-cause err))))))

(defn post-endpoint-x
  "fetch data from route map"
  [route]
  (let
   [{:keys [server endpoint json-params ok-fn err-fn]} route]
    (a/go
      (let [response (a/<! (http/post (make-url server endpoint)
                                      {:with-credentials? false
                                       :json-params json-params}))
            success? (:success response)]
        (if success?
          (let [body (:body response)
                err (:error body)]
            (if (= err 0)
              (ok-fn body)
              (err-fn "fetch error:" err)))
          (do (println (:error-code response) (:error-text response))
              (js/setTimeout (.exit process) 2000))))))
  :fetched)

(defn exit-fn
  "exit the process when exit-chan signal received"
  [svr]
  (println "Closing graph server and exiting")
  (js/setTimeout (fn []
                   (.close svr)
                   (js/setTimeout (.exit process))) 4000))

(defn -main
  "entry point: start graph server and read program specified on cmd line"
  [& args]
  (process/on "uncaughtException", (fn [err origin]
                                     (println "Uncaught Exception" err origin)))
  (let [svr (nhttp/createServer svr-resp-fn)
        prog (first args)]
    (.listen svr 2626 "127.0.0.1")
    (println "Reading program file: " prog)
    (println "fetcher path:" (path-to-fetcher))
    (let [params (merge (read-program prog) {:ok-fn vega-fetch-and-open
                                             :err-fn println})]
      #_(println params)
      (post-endpoint-x params))
    (a/take! exit-chan #(exit-fn svr))))

(comment
  (js/console.log (js* "__dirname"))
  (-main "ukraine.edn")
  @out)





