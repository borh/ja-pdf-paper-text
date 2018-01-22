(ns ja-pdf-paper-text.text
  (:require [clojure.string :as string]
            [me.raynes.conch :as sh]
            [pdfboxing.text :as text]
            [ja-pdf-paper-text.jp :refer [is-japanese? char-writing-system]]
            [corpus-utils.text :as utils]))

(defn remove-title [title journal s]
  (if-let [match (re-seq (re-pattern (format "(%s|%s)" title journal)) s)]
    (let [maybe-sentence (-> s
                             (string/replace title "")
                             (string/replace (re-pattern (format "(?i)%s[\\s\\dvolumen\\.]*" journal)) ""))]
      (if (and (not-empty maybe-sentence) (is-japanese? maybe-sentence ""))
        maybe-sentence
        (println (format "Rejecting: title='%s', journal='%s', s='%s', ms='%s'" title journal s maybe-sentence))))
    s))

(defn remove-space-between-chars [s]
  "Removes spaces between Japanese character classes."
  (if (> (count s) 2)
    (str (subs s 0 2)
         (string/join (filter identity
                              (map (fn [b-c c a-c]
                                     (let [b-c-s (char-writing-system (str b-c))
                                           a-c-s (char-writing-system (str a-c))]
                                       (cond
                                         (and (#{:hiragana :katakana :kanji} b-c-s)
                                              (re-seq #"[\s　]" (str c))
                                              (#{:hiragana :katakana :kanji} a-c-s)) nil
                                         (and (#{:romaji} b-c-s)
                                              (re-seq #"[\s　]" (str c))
                                              (#{:hiragana :katakana :kanji :symbols :commas} a-c-s)) nil
                                         (and (#{:hiragana :katakana :kanji :symbols :commas} b-c-s)
                                              (re-seq #"[\s　]" (str c))
                                              (#{:romaji :commas} a-c-s)) nil
                                         :else c)))
                                   (drop 1 s) (drop 2 s) (drop 3 s))))
         (subs s (- (count s) 1)))))

(defn clean-text [text title journal]
  (if (empty? text)
    ""
    (sequence
     (comp (take-while #(not (or (re-seq #"^.{0,5}(参\s?[考照]|引\s?用)\s?文\s?献" %)
                                 (re-seq #"[【（].{0,4}(参[考照]|引用)文献[】）]" %))))
           (map (partial remove-title title journal))
           (filter identity)
           (remove #(re-seq #"^\s*$" %))
           (filter (partial is-japanese? ""))
           (map utils/convert-half-to-fullwidth))
     (-> (apply str (filter (fn [c] (not (Character/isISOControl c))) text))
         (string/replace #"(?m)\n[\d０-９]+\n" "")
         (string/replace #"\n" "")
         (string/replace #"(\s)\s+" "$1")
         remove-space-between-chars
         utils/split-japanese-sentence))))

(defn multi-extract
  ([path]
   (multi-extract path "" ""))
  ([path title journal]
   (let [text-maps
         [#_{:program :default :text (text/extract path)}
          #_{:program :pdftotext-raw-text :text (sh/with-programs [pdftotext] (pdftotext "-raw" path "-"))}
          {:program :pdftotext-raw-layout-text :text (sh/with-programs [pdftotext] (pdftotext "-raw" "-layout" path "-"))}
          #_{:program :pdftotext-text :text (sh/with-programs [pdftotext] (pdftotext path "-"))} ;; Does not work for
          #_{:program :pdfminer :text (try (sh/let-programs [pdf2txt "/usr/bin/pdf2txt.py"] (pdf2txt "-VA" path))
                                           (catch clojure.lang.ExceptionInfo e ""))}
          {:program :podofotxtextract-text :text (try (sh/with-programs [podofotxtextract] (podofotxtextract path))
                                                      (catch clojure.lang.ExceptionInfo e ""))}]]
     (->> text-maps
          (map
           (fn [m]
             (let [cleaned-text (string/join "\n" (clean-text (:text m) title journal))]
               (-> m
                   (assoc :text cleaned-text)
                   (assoc :chars (count cleaned-text))))))))))
