(ns pdf-ja-paper-text-extractor.test-main
  (:require [pdf-ja-paper-text-extractor.main :refer :all]
            [corpus-utils.utils :as utils]
            [clojure.test :as t]))

#_(let [sources (metadata "/home/bor/Corpora/Social-Sciences-Papers/bib.tsv")]
    (println (take 10 sources))
    (write-tsv-map "sources.tsv" sources))
