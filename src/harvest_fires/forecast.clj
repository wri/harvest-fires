(ns harvest-fires.forecast
  (:require [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]))

(defn get-creds
  "Accepts a target key from credentials.json and returns the
  credentials.  Default is the credentials.json directory at the top
  of the project directory."
  [target-key & {:keys [creds] :or {creds "credentials.json"}}]
  (-> (slurp creds)
      (json/read-str :key-fn keyword)
      (target-key)))

(defn- compose-str
  "A more general version of interpose, capable of handling nils and
  non-strings"
  [sep & args]
  (->> [args]
       (apply remove nil?)
       (interpose sep)
       (apply str)))

(defn forecast
  "Accepts the latitude and longitude, along with the time in
  seconds (not milliseconds) and other optional arguments, and returns
  the associated forecast.io object"
  [lat lon & {:keys [params time] :or [params nil time nil]}]
  (let [base-url "https://api.forecast.io/forecast"
        api-key (get-creds :forecast-key)
        url (compose-str "/" base-url api-key (compose-str "," lat lon time))
        response (client/get url {:query-params params :throw-exceptions false})]
    (cond (= 200 (:status response))
          (cheshire/parse-string (:body response) true))))

