(ns series-downloader.epstring)

(defn next-episode [ep-string]
  (clojure.string/replace ep-string #"\d+$" #(format "%02d" (inc (Integer. %1)))))

(defn next-season [ep-string]
  (format "S%02dE01" (inc (Integer. (re-find #"\d+" ep-string)))))

(defn unpack-ep-string [ep-string]
  (map #(Integer. %) (rest (re-find #"^S(\d+)E(\d+)" ep-string))))

(defn episode< [ep1-string ep2-string]
  (let [[ep1-season ep1-episode] (unpack-ep-string ep1-string)
        [ep2-season ep2-episode] (unpack-ep-string ep2-string)]
    (and (<= ep1-season ep2-season) (< ep1-episode ep2-episode))))
