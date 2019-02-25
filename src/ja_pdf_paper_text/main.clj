(ns ja-pdf-paper-text.main
  (:require [pdfboxing.text :as text]
            [pdfboxing.info :as info]
            [clojure.string :as string]
            [datoteka.core :as fs]
            [corpus-utils.text :as utils]
            [clojure.string :as str]
            [ja-pdf-paper-text.text :refer [multi-extract clean-text remove-title]]
            [ja-pdf-paper-text.jp :refer [is-japanese?]]))

(defn base-name [s]
  (-> s
      (fs/path)
      (fs/name)
      (fs/split-ext)
      (first)))

;; 科研の分類を使う
(def journal-to-category
  (->>
   (slurp "journal-categories.tsv")
   (str/split-lines)
   (reduce
    (fn [a x]
      (let [[journal-name category-1 category-2] (str/split x #"\t")]
        (assoc a journal-name [category-1 category-2])))
    {})))

(defn write-tsv-map [file-name map-seq]
  (with-open [out-file (clojure.java.io/writer file-name)]
    (let [ks (keys (first map-seq))]
      (clojure.data.csv/write-csv out-file [(map name ks)] :separator \tab) ; header
      (doseq [map-vals map-seq]
        (clojure.data.csv/write-csv out-file [(mapv map-vals ks)] :separator \tab)))))

(defn metadata [filename]
  (sequence
   (comp
    (drop 1)
    (map (fn [s] (str/split s #"\t")))
    (map (partial zipmap [:basename :title :author :journal :volume :number :pages :url]))
    (map (fn [m] (-> m
                     (assoc :title-string (:title m))
                     (update :title
                             (fn [title]
                               (format "%s%s: 「%s」"
                                       (:journal m)
                                       (cond
                                         (and (not-empty (:volume m))
                                              (not-empty (:number m)))
                                         (format " (%s/%s)" (:volume m) (:number m))

                                         (not-empty (:volume m))
                                         (format " (%s巻)" (:volume m))

                                         (not-empty (:number m))
                                         (format " (%s号)" (:number m))

                                         :else "")
                                       title)))
                     (assoc :permission false
                            :genre-1 "人文社会学論文"
                            :genre-2 (get-in journal-to-category [(:journal m) 0] (str "NA: " (:journal m)))
                            :genre-3 (get-in journal-to-category [(:journal m) 1] (str "NA: " (:journal m)))
                            :genre-4 (:journal m)
                            :year 2017 #_FIXME)))))
   (str/split-lines (slurp filename))))

(defn extract-text [{:keys [title-string genre-4] :as db-entry} pdf-filename]
  (let [raw-text (try (text/extract pdf-filename)
                      (catch java.lang.UnsupportedOperationException e (println e) ""))]
    (println db-entry)
    (clean-text raw-text title-string genre-4)))

(defn batch-convert! [dir]
  (let [metadata-db (reduce (fn [a m] (assoc a (:basename m) m)) {} (metadata (str (fs/join dir "../bib.tsv"))))]
    (with-open [sources-file (clojure.java.io/writer "sources.tsv")]
      (let [ks [:title :author :year :basename :genre-1 :genre-2 :genre-3 :genre-4 :permission]]
        (doseq [pdf (vec (fs/list-dir dir "*.pdf"))]
          (let [basename (base-name pdf)
                parent   (fs/parent pdf)
                out-fn   (str (fs/join parent basename) ".txt")
                db-entry (update (get metadata-db basename) :year
                                 (fn [year]
                                   (let [pdf-meta-year (:weekYear (bean (get (info/about-doc (str pdf)) "creation-date")))
                                         volume-as-year (let [y (get-in metadata-db [basename :volume])]
                                                          (if (re-seq #"^[12]\d\d\d$" y)
                                                            y))]
                                     (or volume-as-year pdf-meta-year year))))
                text     (->> (str pdf)
                              (extract-text db-entry)
                              (str/join "\n"))]
            (if (< (count text) 200)
              (doseq [{:keys [program text chars]} (multi-extract pdf (:title db-entry) (:journal db-entry))]
                (when (> chars 200)
                  (clojure.data.csv/write-csv sources-file [(mapv db-entry ks)] :separator \tab)
                  (println (format "Failed to extract text from '%s' using PDFBox, but %s worked (%s characters extracted)." pdf program chars))
                  (spit (str out-fn "-" (name program)) text)
                  (spit out-fn text)))
              (do (clojure.data.csv/write-csv sources-file [(mapv db-entry ks)] :separator \tab)
                  (spit out-fn text)))))))))

(defn -main [& args]
  (batch-convert! "/home/bor/Projects/hssrpc/pdfs/"))
