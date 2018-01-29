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
  #_(->>
     (slurp "/home/bor/Corpora/Social-Sciences-Papers/gakkai.tsv")
     (str/split-lines)
     (map (fn [s]
            (let [[number-of-pdfs & [society & info]] (str/split s #"\t+")]
              [society info]))))
  {"佛教文化学会紀要" "",
   "日本學士院紀要" "",
   "ソシオロジ" "",
   "オリエント" "",
   "経済地理学年報" "",
   "カリキュラム研究" "",
   "21世紀東アジア社会学" "",
   "家族研究年報" "",
   "南アジア研究" "",
   "国際情報研究 " "",
   "年報政治学" "",
   "行動経済学" "",
   "日本の神学" "",
   "映画研究" "",
   "美学" "",
   "フランス語フランス文学研究" "",
   "法政論叢" "",
   "法制史研究" "",
   "日本の教育史学" "",
   "映像学" "",
   "近世文藝" "",
   "社会学評論" "",
   "西洋古典学研究" "",
   "ロシア・東欧研究" "",
   "アジア研究" "",
   "社会科教育研究" "",
   "公共選択の研究" "",
   "日本文学" "",
   "経営史学" "",
   "教育社会学研究" "",
   "文化人類学" "",
   "産業学会研究年報" "",
   "生活経済学研究" "",
   "近代日本の創造史" "",
   "英文学研究" "",
   "西洋比較演劇研究" "",
   "現代社会学研究" "",
   "日本経営診断学会論集" "",
   "オーストリア文学" "",
   "社会言語科学" "",
   "季刊地理学" "",
   "法社会学" "",
   "イタリア学会誌" "",
   "英米文化" "",
   "社会情報学" "",
   "東南アジア研究" "",
   "日本近代文学" "",
   "年報社会学論集" "",
   "生命倫理" "",
   "家族社会学研究" "",
   "産学連携学" "",
   "哲学" "",
   "国語科教育" "",
   "ジェンダー史学" "",
   "観光研究" "",
   "国際ビジネス研究" "",
   "村落社会研究ジャーナル" "",
   "オーストラリア研究" "",
   "笑い学研究" "",
   "現代監査" "",
   "社会経済史学" "",
   "時間学研究" "",
   "宗教哲学研究" "",
   "国際P2M学会誌" "",
   "アジア・アフリカ地域研究" "",
   "イギリス・ロマン派研究" "",
   "比較文学" "",
   "内陸アジア史研究" "",
   "交通権" "",
   "アジア経営研究" "",
   "社会学年報" "",
   "年報行政研究" "",
   "アフリカ研究" "",
   "教育学研究" "",
   "書学書道史研究" "",
   "教育学研究ジャーナル" "",
   "東南アジア －歴史と文化－" "",
   "教育學雑誌" "",
   "福祉社会学研究" "",
   "体育・スポーツ哲学研究" ""})

(defn write-tsv-map [file-name map-seq]
  (with-open [out-file (clojure.java.io/writer file-name)]
    (let [ks (keys (first map-seq))]
      (clojure.data.csv/write-csv out-file [(map name ks)] :separator \tab) ; header
      (doseq [map-vals map-seq]
        (clojure.data.csv/write-csv out-file [(mapv map-vals ks)] :separator \tab)))))

(defn metadata [filename]
  (->> (slurp filename)
       (str/split-lines)
       (drop 1)
       (map (fn [s] (str/split s #"\t")))
       (map (partial zipmap [:basename :title :author :journal :volume :number :pages :url]))
       (map (fn [m] (-> m
                        (assoc :title-string (:title m))
                        (update :title (fn [title] (format "%s (%s/%s): 「%s」"
                                                           (:journal m) (:volume m) (:number m) title)))
                        (dissoc :volume :number :pages :journal)
                        (assoc :permission false
                               :genre-1 "人文社会学論文コーパス"
                               :genre-2 (:journal m)
                               :genre-3 ""
                               :genre-4 ""
                               :year 2017))))))

(defn extract-text [{:keys [title-string genre-2] :as db-entry} pdf-filename]
  (let [raw-text (try (text/extract pdf-filename)
                      (catch java.lang.UnsupportedOperationException e (println e) ""))
        pdf-meta (info/about-doc pdf-filename)]
    (println db-entry)
    (clean-text raw-text title-string genre-2)))

(defn batch-convert! [dir]
  (let [metadata-db (reduce (fn [a m] (assoc a (:basename m) m)) {} (metadata (str (fs/join dir "../bib.tsv"))))]
    (with-open [sources-file (clojure.java.io/writer "sources.tsv")]
      (let [ks [:title :author :year :basename :genre-1 :genre-2 :genre-3 :genre-4 :permission]]
        (doseq [pdf (vec (fs/list-dir dir "*.pdf"))]
          (let [basename (base-name pdf)
                parent   (fs/parent pdf)
                out-fn   (str (fs/join parent basename) ".txt")
                db-entry (assoc (get metadata-db basename)
                                :year (:weekYear (bean (get (info/about-doc (str pdf)) "creation-date"))))
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
