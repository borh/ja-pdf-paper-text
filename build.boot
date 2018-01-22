(require '[clojure.java.shell :as sh])

(defn next-version [version]
  (when version
    (let [[a b] (next (re-matches #"(.*?)([\d]+)" version))]
      (when (and a b)
        (str a (inc (Long/parseLong b)))))))

(defn deduce-version-from-git
  "Avoid another decade of pointless, unnecessary and error-prone
  fiddling with version labels in source code."
  []
  (let [[version commits hash dirty?]
        (next (re-matches #"(.*?)-(.*?)-(.*?)(-dirty)?\n"
                          (:out (sh/sh "git" "describe" "--dirty" "--long" "--tags" "--match" "[0-9].*"))))]
    (try
      (cond
        dirty? (str (next-version version) "-" hash "-dirty")
        (pos? (Long/parseLong commits)) (str (next-version version) "-" hash)
        :otherwise version)
      (catch Exception e (println "Not a git repository or empty repository. Please git init in this directory/make a commit.")))))

(def project "pdf-ja-paper-text-extractor")
(def version (deduce-version-from-git))

(set-env!
 :source-paths #{"src" "test"}
 :resource-paths #{"resources"}
 :dependencies
 '[[adzerk/boot-reload "0.5.2" :scope "test"]

   [adzerk/boot-test "1.2.0" :scope "test"]
   [tolitius/boot-check "0.1.6" :scope "test"]
   [org.clojure/test.check "0.10.0-alpha2" :scope "test"]

   [org.clojure/clojure "1.9.0"]
   [org.clojure/tools.nrepl "0.2.13"]

   [funcool/datoteka "1.0.0"]
   [me.raynes/conch "0.8.0"]
   [pdfboxing "0.1.13"]
   [org.bouncycastle/bcprov-jdk15on "1.59"]
   [corpus-utils "0.2.10"]
   [com.ibm.icu/icu4j "60.2"]])

(require '[adzerk.boot-test :refer :all]
         '[adzerk.boot-reload :refer [reload]]
         '[tolitius.boot-check :as check]
         '[pdf-ja-paper-text-extractor.main])

(task-options!
 pom {:project (symbol project)
      :version version
      :description "PDF Japanese Paper Text Extractor"
      :license {"The MIT License (MIT)" "http://opensource.org/licenses/mit-license.php"}}
 aot {:namespace #{'pdf-ja-paper-text-extractor.main}}
 jar {:main 'pdf-ja-paper-text-extractor.main
      :file (str project "-app.jar")}
 target {:dir #{"target"}})

(deftask check-sources []
  (set-env! :source-paths #{"src"})
  (comp
   (check/with-yagni)
   (check/with-eastwood)
   (check/with-kibit)
   (check/with-bikeshed)))

(deftask build
  "Build and install the project locally."
  []
  (comp
   (pom)
   (jar)
   (install)))

(deftask dev
  "This is the main development entry point."
  []
  (set-env! :dependencies #(vec (concat % '[[reloaded.repl "0.2.3"]])))

  (comp
   (watch)
   (build)
   (speak)
   (repl :init-ns 'pdf-ja-paper-text-extractor.main :server true)
   (target)))

(deftask run []
  (pdf-ja-paper-text-extractor.main/-main))

(require '[adzerk.boot-test :refer [test]])

(deftask uberjar
  "Build an uberjar"
  []
  (comp
   (build)
   (aot)
   (pom)
   (uber)
   (jar)
   (target)))
