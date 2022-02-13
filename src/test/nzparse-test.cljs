(ns test.nzparse-test
  (:require [nzparse :as nzp :refer [parse reset-symbol-table! setup-sym-table-for-test!
                                     analyze]]
            [cljs.test :refer-macros [deftest is testing run-tests]]))


(deftest parser
  (testing "parser checks"
    (reset-symbol-table!)
    (setup-sym-table-for-test!)
    (let
     [r1 (parse "between 1921-12-24 and 2021-03-12;")
      r2 (parse "between 1921-12-24T12:00:09 and 2021-03-12T20:18:01z;")
      r3 (analyze (parse "Find in [ $nonexistent ] from last 2m;"))
      r4 (analyze (parse "Find in [$fra] from last 24h;"))
      r5 (analyze (parse "Find in [$fra $nonexistent] from last 12h;"))
      r6 (analyze (parse "Find in [$fra $ger Biden] from last 6d;"))]
      (testing "between dates"
        (is (= (:tag (first r1)) :DATWEEN)))
      (testing "between datetimes"
        (is (= (:tag (first r2)) :DTTWEEN)))
      (testing "nonexistent symbol"
        (is (= r3 ["Parse error: symbol not defined"])))
      (testing "findlast with symbol"
        (is (= (:time r4) "24h")))
      (testing "find with nonexistent symbol"
        (is (= r5 ["Parse error: symbol not defined"])))
      (testing "find symbols + word from last 6d"
        (is (= (first (:words r6)) "Macron"))))))

(comment
  (run-tests))

