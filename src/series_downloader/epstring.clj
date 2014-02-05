(ns series-downloader.epstring)

(defn from-int [season-number episode-number]
  (let [formatter #(format "%02d" (int %))]
    (str "S" (formatter season-number) "E" (formatter episode-number))))

(defn from-episode-list [episode-list]
  (for [ep episode-list]
   (series-downloader.epstring/from-int (ep :season-number) (ep :episode-number))
   ))

(defn to-int [ep-string]
  (map #(Integer. %) (rest (re-find #"^S(\d+)E(\d+)" ep-string))))

(defn next-episode [ep-string]
  (clojure.string/replace ep-string #"\d+$" #(format "%02d" (inc (Integer. %1)))))

(defn next-season [ep-string]
  (format "S%02dE01" (inc (Integer. (re-find #"\d+" ep-string)))))

(defn episode< [ep1-string ep2-string]
  (let [[ep1-season ep1-episode] (to-int ep1-string)
        [ep2-season ep2-episode] (to-int ep2-string)]
    (and (<= ep1-season ep2-season) (< ep1-episode ep2-episode))))
