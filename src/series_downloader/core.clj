(ns series-downloader.core
  (:gen-class)
  [:import
   [java.net URL URLEncoder]
   [javax.xml parsers.DocumentBuilderFactory xpath.XPathFactory]])
(use 'clojure.java.io
     'series-downloader.epstring)

(def tvdb-file "tvdb.clj")
(def tvdb-api-key "6C4BF2A213C012E2")

(defn deserialize [file]
  (read-string (slurp file)))

(defn serialize [file tvdb]
  (spit file (pr-str tvdb)))

(def read-tvdb (partial deserialize tvdb-file))
(def write-tvdb (partial serialize tvdb-file))

(def tvdb (atom {}))

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
  (println "Getting id from tvdb for" series-name)
  (xpath-from-url
   (str "http://thetvdb.com/api/GetSeries.php?seriesname="
        (URLEncoder/encode series-name))
   "//Data/Series/id"))

(defn episode-list-from-tvdb [series-id])


(defn series-id [tvdb series-name]
  (or (get-in tvdb [series-name :id])
      (series-id-from-tvdb series-name)))

(defn episode-list [series-id])


(defn series-ids [tvdb]
  (let [newdb (atom tvdb)]
    (doseq [[name] tvdb]
      (swap! newdb assoc-in [name :id] (series-id tvdb name)))
    @newdb))

(defn episode-lists [tvdb]
  (let [newdb (atom tvdb)]
    (doseq [[name {:keys [id]}] tvdb]
      (swap! newdb assoc-in [name :episodes] (episode-list id)))
    @newdb))

(defn download-show [[name last-downloaded]]
  (println "Checking air dates for" name))

(swap! tvdb (fn [_] (read-tvdb)))
(swap! tvdb series-ids)
(episode-lists @tvdb)
(write-tvdb @tvdb)

(defn -main [& args]
  (println "Starting Series Downloader...")
  )
