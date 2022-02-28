# xgraph -- view graphs constructed by querying the nooze database

## installation

npm i -g

## use

xgraph progfile.edn

## program files

A program file looks like this:

```clojure
{:endpoint "/json/xgraph"
 :server "https://eu1.noozewire.com"
 :json-params {:subqueries [["Ukraine" "Ucraina"] ["Putin" "Poutine" "Lavrov" "Lavrow"]
                            ["Zelensky" "Selenskij" "Zelenskii"]
                            ["Biden" "Blinken"]]
               :start "2021-09-01"
               :title "Ukraine"
               :interval "30d" :n 6}}

```
