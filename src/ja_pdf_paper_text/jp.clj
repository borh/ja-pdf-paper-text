(ns ja-pdf-paper-text.jp)

(defn char-writing-system
  "Return writing system type of char.
  To get codepoint of char: `(.codePointAt char 0)`.
  Converting code point into char: `(char 0x3041)`."
  [^String ch]
  (let [code-point (.codePointAt ch 0)]
    (cond
      (and (>= code-point 0x3041) (<= code-point 0x309f)) :hiragana
      (and (>= code-point 0x4e00) (<= code-point 0x9fff)) :kanji
      (and (>= code-point 0x30a0) (<= code-point 0x30ff)) :katakana
      (or (and (>= code-point 65)    (<= code-point 122))    ; half-width alphabet (A-Za-z)
          (and (>= code-point 65313) (<= code-point 65370))  ; full-width alphabet (Ａ-Ｚａ-ｚ)
          (and (>= code-point 48)    (<= code-point 57))     ; half-width numbers  (0-9)
          (and (>= code-point 65296) (<= code-point 65305))) ; full-width numbers  (０-９)
      :romaji
      (or (= code-point 12289) (= code-point 65291) (= code-point 44)) :commas ; [、，,] <- CHECK
      :else :symbols)))

(defn writing-system-count
  "Returns a hash-map of character frequencies by writing system."
  [s]
  (merge {:hiragana 0
          :kanji    0
          :katakana 0
          :romaji   0
          :symbols  0
          :commas   0}
         (frequencies (map char-writing-system (map str s)))))

(defn is-japanese? [filename s]
  (let [ws-freqs (writing-system-count s)
        length (count s)
        japanese-ratio (if (pos? length)
                         (/ (reduce + (vals (select-keys ws-freqs [:hiragana :katakana :kanji :commas]))) length)
                         0.0)]
    (if (> japanese-ratio 0.5)
      true)))
