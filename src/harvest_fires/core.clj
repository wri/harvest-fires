(ns harvest-fires.core
  (:use cascalog.api
        cartodb.playground)
  (:require [clj-http.client :as client]
            [clojure-csv.core :as csv]
            [clojure.data.json :as json]))

(defn get-creds
  "Accepts a target key from credentials.json and returns the
  credentials.  Default is the credentials.json directory at the top
  of the project directory."
  [target-key & {:keys [creds] :or {creds "credentials.json"}}]
  (-> (slurp creds)
      (json/read-str :key-fn keyword)
      (target-key)))

(defn se-query
  "Returns the fire data for the last 24 hours for all of Southeast
  Asia as vectors of strings; the first entry is column names"
  []
  (let [base "http://firms.modaps.eosdis.nasa.gov"
        params "active_fire/text/SouthEast_Asia_24h.csv"
        url  (str base "/" params)
        response (client/get url)]
    (cond (= 200 (:status response))
          (csv/parse-csv (:body response)))))

(defn convert-fires
  "Accepts the output from `se-query` and returns an array of
  dictionaries for each of the downloaded fires, using the first row
  as keys.

  Example usage:
    (convert-fires (se-query))"
  [fire-data]
  (let [cols (map keyword (first fire-data))]
    (map (partial zipmap cols)
         (rest fire-data))))

(defn cull-features
  "Accepts a single fire dictionary and returns the cleaned and culled
  fire, ready for upload into the cartodb table."
  [fire]
  (select-keys fire [:latitude :longitude :acq_date :confidence]))

(defn hard-read
  "Returns the number of the supplied string, unless it cannot be read
  in which case returns the untransformed string."
  [s]
  (let [try-s (try (read-string s) (catch Exception e))]
    (if (nil? try-s) s (read-string s))))

(defn limit-fires
  "Returns all fires (properly formatted) that are more southern
  7.67N, which is the northernmost latitude for Indonesia."
  [fire-data]
  (for [fire fire-data
        :when (< (-> fire :latitude read-string)
                 7.67)]
    (map hard-read (vals (cull-features fire)))))

(defmain UploadFires
  "Uploads the most recent fires to cartodb table"
  []
  (let [fires (->> (se-query) convert-fires limit-fires)
        creds (get-creds :cartodb-creds)]
    (do (delete-all "wri-01" creds "recent_fires")
        (apply insert-rows "wri-01" creds "recent_fires"
               [:conf :date :latitude :longitude]
               fires))))
