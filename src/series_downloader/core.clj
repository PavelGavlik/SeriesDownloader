(ns series-downloader.core
  (:gen-class)
  [:import
   [java.net URL URLEncoder]
   [javax.xml parsers.DocumentBuilderFactory xpath.XPathFactory xpath.XPathConstants]])
(use 'clojure.java.io
     'clojure.pprint
     'series-downloader.epstring)

(def tvdb-file "tvdb.clj")
(def tvdb-api-key "6C4BF2A213C012E2")
(def tvdb (atom {}))


(defn deserialize [file]
  (read-string (slurp file)))

(defn serialize [file tvdb]
  (spit file
        (pr-str tvdb)
        ;(with-out-str (clojure.pprint/pprint tvdb))
        ))

(def read-tvdb (partial deserialize tvdb-file))
(def write-tvdb (partial serialize tvdb-file))


(defn slurp-url [& url-parts]
  (with-open [rdr (clojure.java.io/reader (clojure.string/join url-parts))]
         (clojure.string/join "\n" (line-seq rdr))))

(defn node-list-to-lazy-seq [node-list]
  (for [i (range (.getLength node-list))]
    (.item node-list i)))

(defn map-node-list [f node-list]
  (map f (node-list-to-lazy-seq node-list)))

(defn xml-document [filename]
  (-> (DocumentBuilderFactory/newInstance)
      .newDocumentBuilder
      (.parse filename)))

(defn xml-remote-document [& url-parts]
  (xml-document (.openStream (URL. (clojure.string/join url-parts)))))

(defn xpath-string [document xpath-expr]
  (-> (XPathFactory/newInstance)
      .newXPath
      (.evaluate xpath-expr document)))

(defn xpath-nodelist [document xpath-expr]
  (-> (XPathFactory/newInstance)
      .newXPath
      (.evaluate xpath-expr document XPathConstants/NODESET)))


(defn series-id-from-tvdb [series-name]
  (println "Getting id from tvdb for" series-name)
  (xpath-string
   (xml-remote-document "http://thetvdb.com/api/GetSeries.php?seriesname="
                        (URLEncoder/encode series-name))
   "//Data/Series/id"))

(defn episode-list-from-tvdb [series-name series-id]
  (println "Getting episode list for" series-name)
  (map-node-list #(hash-map
                   :name (xpath-string % "EpisodeName")
                   :aired (xpath-string % "FirstAired")
                   :episode-number (xpath-string % "EpisodeNumber")
                   :season-number (xpath-string % "SeasonNumber")
                   )
                 (xpath-nodelist (xml-remote-document "http://thetvdb.com/api/"
                                                      tvdb-api-key "/series/"
                                                      series-id "/all/en.xml")
                                 "//Data/Episode")))


(defn series-id [tvdb series-name]
  (or (get-in tvdb [series-name :id])
      (series-id-from-tvdb series-name)))

(defn episode-list [tvdb series-name series-id]
  (or (get-in tvdb [series-name :episodes])
      (group-by :season-number
                (episode-list-from-tvdb series-name series-id))))


(defn series-ids [tvdb]
  (let [newdb (atom tvdb)]
    (doseq [[name] tvdb]
      (swap! newdb assoc-in [name :id] (series-id tvdb name)))
    @newdb))

(defn episode-lists [tvdb]
  (let [newdb (atom tvdb)]
    (doseq [[name {:keys [id]}] tvdb]
      (swap! newdb assoc-in [name :episodes] (episode-list tvdb name id)))
    @newdb))

(defn download-show [[name last-downloaded]]
  (println "Checking air dates for" name))

;(swap! tvdb (fn [_] (read-tvdb)))
;(swap! tvdb series-ids)
;(swap! tvdb episode-lists)
;(write-tvdb @tvdb)

(defn -main [& args]
  (println "Starting Series Downloader...")
  )
