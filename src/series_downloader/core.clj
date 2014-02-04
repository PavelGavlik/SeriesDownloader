(ns series-downloader.core
  (:gen-class)
  [:import
   [java.net URL URLEncoder]
   [java.text SimpleDateFormat]
   [java.util Date]
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

(defn xpath [cast-type document xpath-expr]
  (-> (XPathFactory/newInstance)
      .newXPath
      (.evaluate xpath-expr document cast-type)))

(def xpath-string (partial xpath XPathConstants/STRING))
(def xpath-number (partial xpath XPathConstants/NUMBER))
(def xpath-node-list (partial xpath XPathConstants/NODESET))


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
                   :episode-number (xpath-number % "EpisodeNumber")
                   :season-number (xpath-number % "SeasonNumber")
                   )
                 (xpath-node-list (xml-remote-document "http://thetvdb.com/api/"
                                                       tvdb-api-key "/series/"
                                                       series-id "/all/en.xml")
                                  "//Data/Episode")))


(defn series-id [tvdb series-name]
  (or (get-in tvdb [series-name :id])
      (series-id-from-tvdb series-name)))

(defn episode-list [tvdb series-name series-id]
  (or (get-in tvdb [series-name :episodes])
      (episode-list-from-tvdb series-name series-id)))


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

(defn available-episode-list [series]
  (filter
   #(and
     (>= (% :season-number) (series :last-downloaded-season))
     (> (% :episode-number) (series :last-downloaded-episode))
     (= 10 (count (% :aired)))
     (->
      (SimpleDateFormat. "yyyy-MM-dd")
      (.parse (% :aired))
      (.before (Date.))))
   (series :episodes)))

(defn download-torrent [torrent-name]
  (println "Downloading" torrent-name))

(defn download-one-series [series-name series-data]
  (let [download-list (->
                       series-data
                       available-episode-list)
        last-episode (last download-list)]
    (map #(download-torrent (str series-name %))
         (series-downloader.epstring/from-episode-list download-list))
    (if last-episode
      (assoc series-data
        :last-downloaded-season (last-episode :season-number)
        :last-downloaded-episode (last-episode :episode-number))
      series-data)))

(defn download-series [tvdb]
  (let [newdb (atom tvdb)]
    (doseq [[name data] tvdb]
      (swap! newdb assoc-in [name] (download-one-series name data)))
    @newdb))


(swap! tvdb (fn [_] (read-tvdb)))
;(swap! tvdb series-ids)
;(swap! tvdb episode-lists)
;(swap! tvdb download-series)
;(write-tvdb @tvdb)

(defn -main [& args]
  (println "Starting Series Downloader...")
  )
