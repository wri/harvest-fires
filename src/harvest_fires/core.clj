(ns harvest-fires.core
  (:use harvest-fires.forecast
        cascalog.api
        cartodb.playground
        clj-time.format
        [clojure.set :only (rename-keys)]
        [clj-time.core :only (date-time)]
        [clojure.string :only (trim split)])
  (:require [clj-http.client :as client]
            [clojure-csv.core :as csv]
            [clojure.data.json :as json]))

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

(defn format-date
  "Accepts a fire dictionary and returns the dictionary with a new
  key-value pair indicating the date and time of fire acquisition."
  [fire]
  (let [[t d] (map fire [:acq_time :acq_date])
        input-format (formatter "yyyy-MM-dd-HHmm")
        final-format (formatters :date-hour-minute-second)]
    (assoc fire :date (->> (trim t)
                           (str d "-")
                           (parse input-format)
                           (unparse final-format)))))

(defn proj-wind-attr
  [forecast-response]
  (let [proj-weather (->> forecast-response :daily :data second)]
    (rename-keys (select-keys proj-weather [:windSpeed :windBearing])
                 {:windSpeed :projWindSpeed :windBearing :projWindBearing})))

(defn current-wind-attr
  [forecast-response]
  (let [weather (->> forecast-response :currently)]
    (select-keys weather [:windSpeed :windBearing])))

(defn wind-attr
  [fire]
  (let [response (forecast (:latitude fire) (:longitude fire))]
    (apply merge fire
           (current-wind-attr response)
           (proj-wind-attr response))))


(defn cull-features
  "Accepts a single fire dictionary and returns the cleaned and culled
  fire, ready for upload into the cartodb table."
  [fire]
  (select-keys fire [:latitude :longitude :date :confidence
                     :windSpeed :projWindSpeed :windBearing :projWindBearing]))

(defn hard-read
  "Returns the number of the supplied string, unless it cannot be read
  in which case returns the untransformed string."
  [s]
  (let [try-s (try (read-string s) (catch Exception e))]
    (if (nil? try-s) s (read-string s))))

(defn in-bounds?
  [fire]
  (let [[lat lon] (map (comp read-string fire)
                       [:latitude :longitude])]
    (and (> lat -1.63) (< lat 2.72)
         (> lon 100.8) (< lon 104.8))))

(defn limit-fires
  "Returns all fires (properly formatted) that are more southern
  7.67N, which is the northernmost latitude for Indonesia."
  [fire-data]
  (for [fire fire-data :when (in-bounds? fire)]
    (map hard-read
         (->> fire format-date wind-attr cull-features vals))))

(defmain UploadFires
  "Uploads the most recent fires to cartodb table"
  [fires]
  (let [
        ;; fires (->> (se-query) convert-fires limit-fires)
        creds (get-creds :cartodb-creds)]
    (do (delete-all "wri-01" creds "recent_fires")
        (apply insert-rows "wri-01" creds "recent_fires"
               [:conf :date :latitude :longitude]
               fires))))
