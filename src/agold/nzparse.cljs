(ns nzparse
  (:require [instaparse.core :as insta]
            [process :as pr]))

(pr/on "uncaughtException", (fn [err origin]
                              (println "Uncaught Exception " err origin)))

(def parse
  (insta/parser
   "<S> = FINDLAST | DEF | DATWEEN | DTTWEEN
    FINDLAST = <FINDIN> <LBKT> (SYMBOL | WORD )+ <RBKT>
       <FROMLAST> TUNITS <SEMI>
    LBKT = '['
    RBKT = ']'
    SEMI = ';'
    FINDIN = 'Find in '
    FROMLAST = 'from last '
    WORD = #'[a-zA-Z0-9]+'
    SYMBOL = #'\\$\\w+'
    TUNITS = #'\\d+[hmd]'
    DEF = <DEFPFX> SYMBOL <LBKT> (SYMBOL | WORD)+ <RBKT> <SEMI>
    DEFPFX = 'Define '
    DATWEEN = <'between'> DATE <'and'> DATE <SEMI>
    DTTWEEN = <'between'> DATE TIME <'and'> DATE TIME <SEMI>
    DATE = #'[12][09][0-9]{2}-[01]\\d-\\d{2}'
    TIME = #'T?[012][0-9]:\\d{2}:\\d{2}z?'
    "
   :auto-whitespace :standard
   :output-format :enlive))

(def symbol-table (atom {}))

(defn reset-symbol-table!
  "reset the symbol table to empty map"
  []
  (reset! symbol-table {}))

(defn add-symbol!
  "add symbol to symbol table"
  [symbol vec-of-words]
  (swap! symbol-table assoc symbol vec-of-words))

(defn parse-error
  "deal with parse error in node"
  [node msg]
  (println "Error:" node)
  (throw (ex-info (str "Parse error: " msg) {:node node})))

(defn build-findlast
  "reducing fn to build a findlast command"
  [acc node]
  (let [tag (:tag node)
        content (first (:content node))
        symbol-content (get @symbol-table content)]
    (when (and (= tag :SYMBOL) (nil? symbol-content))
      (parse-error node "symbol not defined"))
    (condp = tag
      :WORD (update-in acc [:words] conj content)
      :SYMBOL (update-in acc [:words] into symbol-content)
      :TUNITS (assoc-in acc [:time] content))))

;; FINDLAST content looks like this
;; ({:tag :WORD, :content ("worda")}
;;   {:tag :WORD, :content ("wordb")}
;;   {:tag :SYMBOL, :content ("$topic")}
;;   {:tag :HOURS, :content ("2")})
;; TODO error checking
(defn analyze-findlast
  "analyze the node of type FINDLAST"
  [content]
  #_(println "analyze-findlast" content)
  (let [command {:words [] :time ""}]
    (reduce build-findlast command content)))

;; DEF content looks like
;; ({:tag :SYMBOL, :content ("$fra")}
;;  {:tag :WORD, :content ("Macron")}
;;  {:tag :WORD, :content ("Castex")})
(defn analyze-def
  "analyze node of type DEF"
  [content]
  (let [symbol-node (first content)
        symbol (first (:content symbol-node))
        tag (:tag symbol-node)
        vec-of-words (mapv (comp first :content) (rest content))]
    (when (not= tag :SYMBOL) ;; sanity check
      #_(println "Error in symbol node" symbol-node)
      (parse-error content "symbol error"))
    (add-symbol! symbol vec-of-words)))

;; parser output looks like
;; ({:tag :FINDLAST
;;  :content
;;  ({:tag :WORD, :content ("worda")}
;;   {:tag :WORD, :content ("wordb")}
;;   {:tag :SYMBOL, :content ("$topic")}
;;   {:tag :HOURS, :content ("2")})})

(defn analyze
  "analyze parser output"
  [parsed]
  (try
    (let [node (first parsed)
          content (:content node)]
      (when (:index parsed) ;; in case of analyzer error
        (parse-error parsed "Incorrect syntax"))
      (condp = (:tag node)
        :FINDLAST (analyze-findlast content)
        :DEF (analyze-def content)
        "No matching node type"))
    (catch js/Error e [(ex-message e)])))

(defn setup-sym-table-for-test!
  "set up the symbol table for resting"
  []
  (analyze (parse "Define $fra [Macron Castex];"))
  (analyze (parse "Define $ger [Germany France];")))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "I'm here: " args))


(comment
  @symbol-table
  (reset-symbol-table!)
  (setup-sym-table-for-test!)
  (parse "between 1921-12-24 and 2021-03-12;")
  (parse "between 1921-12-24T12:00:09 and 2021-03-12T20:18:01z;")
  (analyze (parse "Find in [ $nonexistent ] from last 2m;"))
  (analyze (parse "Find in [$fra] from last 24h;"))
  (analyze (parse "Find in [$fra $nonexistent] from last 12h;"))
  (analyze (parse "Find in [$fra $ger Biden] from last 6d;")))
