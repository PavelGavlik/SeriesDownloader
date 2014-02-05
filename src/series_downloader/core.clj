(ns series-downloader.core
  (:gen-class)
  (:require [clj-http.client :as client])
  [:import
   [java.net URL URLEncoder]
   [java.io.*]
   [java.text SimpleDateFormat]
   [java.util Date]
   [javax.xml parsers.DocumentBuilderFactory xpath.XPathFactory xpath.XPathConstants]])
(use 'clojure.pprint
     'series-downloader.epstring)

(def tvdb-file "tvdb.clj")
(def tvdb-api-key "6C4BF2A213C012E2")
(def tvdb (atom {}))


(defn deserialize [file]
  (read-string (slurp file)))

(defn serialize [file tvdb]
  (spit file
        (pr-str tvdb)))

(def read-tvdb (partial deserialize tvdb-file))
(def write-tvdb (partial serialize tvdb-file))

(defn sh [& url-parts]
  (->
   (Runtime/getRuntime)
   (.exec (clojure.string/join url-parts))
   .getInputStream
   clojure.java.io/reader
   line-seq
   clojure.string/join))

(defn slurp-url [& url-parts]
  ((clj-http.client/get (clojure.string/join url-parts)) :body))

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
  (sh "open "
      (last
       (re-find #"href=\"([^\">]*)\"[^>]*>Magnet Link"
                (slurp-url "http://bitsnoop.com"
                           (last
                            (re-find #"<a href=\"(.*?)\".*?<span id=\"hdr"
                                     (slurp-url "http://bitsnoop.com/search/all/" torrent-name "/c/d/1/"))))))))

(defn download-one-series [series-name series-data]
  (let [download-list (->
                       series-data
                       available-episode-list)
        last-episode (last download-list)]
    (println series-name download-list)
    (for [ep (series-downloader.epstring/from-episode-list download-list)]
      (download-torrent (str series-name " " ep))

    )))
;;     (if last-episode
;;       (assoc series-data
;;         :last-downloaded-season (last-episode :season-number)
;;         :last-downloaded-episode (last-episode :episode-number))
;;       series-data)))

(defn download-series [tvdb]
  (let [newdb (atom tvdb)]
    (for [[name data] tvdb]
      (swap! newdb assoc-in [name] (download-one-series name data)))))

(defn -main [& args]
  (println "Starting Series Downloader...")
  (swap! tvdb (fn [_] (read-tvdb)))
  (swap! tvdb series-ids)
  (swap! tvdb episode-lists)
  (download-series @tvdb)
  (write-tvdb @tvdb))

(defn fact [n]
  (reduce *' (range 1 (+ n 1))))

(defn -parallel []
  (println "Testing sequential factorials")
  (time (doall (map #(fact %) (range 1 2000))))
  (println "Testing parallel factorials")
  (time (doall (pmap #(fact %) (range 1 2000))))
  (shutdown-agents)
)
