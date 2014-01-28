(ns series-downloader.core
  (:gen-class)
  [:import
   [java.net URL URLEncoder]
   [java.io BufferedReader InputStreamReader]
   [javax.xml parsers.DocumentBuilderFactory xpath.XPathFactory]])
(use 'clojure.java.io
     'series-downloader.epstring)

(def tvdb-file "tvdb.clj")
(def series-file "series.clj")
(def tvdb-api-key "6C4BF2A213C012E2")

(defn deserialize [file]
  (read-string (slurp file)))

(defn serialize [file tvdb]
  (spit file (pr-str tvdb)))

(def read-tvdb (partial deserialize tvdb-file))
(def write-tvdb (partial serialize tvdb-file))
(def read-series (partial deserialize series-file))
(def write-series (partial serialize series-file))


(defn slurp-url [& url-parts]
  (with-open [rdr (clojure.java.io/reader (clojure.string/join url-parts))]
         (clojure.string/join "\n" (line-seq rdr))))

(defn xml-document [filename]
  (-> (DocumentBuilderFactory/newInstance)
      .newDocumentBuilder
      (.parse filename)))

(defn xpath [document xpath-expr]
  (-> (XPathFactory/newInstance)
      .newXPath
      (.evaluate xpath-expr document)))

(defn xpath-from-url [url xpath-expr]
  (xpath (xml-document (.openStream (URL. url))) xpath-expr))


(defn series-id-from-tvdb [series-name]
  (xpath-from-url
   (str "http://thetvdb.com/api/GetSeries.php?seriesname=" (URLEncoder/encode series-name))
   "//Data/Series/id"))

(defn series-id-from-cache [series-name]
  (get-in @tvdb [series-name :id]))

(defn series-id [series-name]
  (or (series-id-from-cache series-name)
      (series-id-from-tvdb series-name)))

(defn fill-tvdb [tvdb]
  (for [name (keys @tvdb)]
    (reset! tvdb (update-in @tvdb [name :id] (fnil identity (series-id name)))))
  nil)
;;(.getDate (get (read-tvdb) "How I Met Your Mother"))
;; (write-tvdb {"How I Met Your Mother" (java.util.Date.)})
;;(map series-id (keys (read-series)))
(def tvdb (atom (read-series)))
@tvdb
(fill-tvdb tvdb)

;(def tvdb [{:name "How I Met Your Mother" :last-downloaded "S09E15"}
;           {:name "Archer" :last-downloaded "S05E02"}
;           {:name "Blacklist" :last-downloaded "S01E12"}])

(defn download-show [[name last-downloaded]]
  (println "Checking air dates for" name))

(defn -main [& args]
  (println "Starting Series Downloader...")
  (with-open [rdr (reader series-file)]
    (doseq [line (line-seq rdr)]
      (download-show (clojure.string/split line #";")))))
