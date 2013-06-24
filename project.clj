(defproject harvest-fires "0.1.0-SNAPSHOT"
  :description "Download and process the NASA fire data for the
  previous 24 hour period."
  :url "datalab.wri.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :resources-path "resources"
  :repositories {"conjars" "http://conjars.org/repo/"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [clj-http "0.4.3"]
                 [cheshire "4.0.0"]
                 [clj-time "0.5.0"]
                 [cartodb-clj "1.5.2"]
                 [cascalog "1.10.2-SNAPSHOT"]
                 [org.clojure/data.json "0.2.1"]
                 [clojure-csv/clojure-csv "2.0.0-alpha1"]
                 [org.clojure/clojure-contrib "1.2.0"]]
  :profiles {:dev {:dependencies [[org.apache.hadoop/hadoop-core "0.20.2-dev"]]
                   :plugins [[lein-swank "1.4.4"]
                             [lein-midje "2.0.0-SNAPSHOT"]]}})
