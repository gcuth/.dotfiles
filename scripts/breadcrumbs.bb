#!/usr/bin/env bb
;; 
;; A script to find (and extract) all lat/long positions from (incidental) log
;; files created by various Shortcuts.

(require '[babashka.fs :as fs]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.instant :as inst]
         '[babashka.process :refer [shell]])

(def icloud-log-dir "~/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/")

(def icloud-addresses-path
  "~/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/Personal Details/addresses.json")

(defn list-json-files
  "List all files in a given path (recursively) ending in .json"
  [path]
  (fs/glob path "**/*.json"))

(defn get-valid-positions
  "Filter a list of files for those containing valid/accurate lat/long positions."
  [files]
  (->> files
       (filter #(not= % nil))
       (map #(fs/unixify %))
       (map #(json/parse-string (slurp %) true))
       (filter #(contains? % :latitude))
       (filter #(contains? % :longitude))
       (filter #(contains? % :datetime))
       (filter #(contains? % :device-model))
       (filter #(str/includes? (:device-model %) "iPhone"))
       (map #(select-keys % [:latitude :longitude :datetime]))
       ;; read lat/long as numbers
       (map #(update-in % [:latitude] edn/read-string))
       (map #(update-in % [:longitude] edn/read-string))
       ;; associate a unix timestamp 
       (map #(assoc % :timestamp (.getTime (inst/read-instant-date (:datetime %)))))))

(defn sort-positions
  "Sort a list of position maps by datetime."
  [positions]
  (sort-by :datetime positions))

(defn count-days
  "Count the number of days in a list of position maps"
  [positions]
  (->> positions
       (map #(str/split (:datetime %) #"T"))
       (map #(first %))
       (set)
       (count)))

(defn deg-to-rad
  "Converts degrees to radians"
  [deg]
  (* deg (/ Math/PI 180)))

(defn haversine-distance
  "Calculates the Haversine distance between two geographic coordinates"
  [lat1 lon1 lat2 lon2]
  (let [r-earth 6371 ;; Earth radius in kilometers
        d-lat (deg-to-rad (- lat2 lat1))
        d-lon (deg-to-rad (- lon2 lon1))
        a (+ (* (Math/sin (/ d-lat 2)) (Math/sin (/ d-lat 2)))
             (* (Math/cos (deg-to-rad lat1))
                (Math/cos (deg-to-rad lat2))
                (Math/sin (/ d-lon 2))
                (Math/sin (/ d-lon 2))))
        c (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))
        d (* r-earth c)]
    d))

(defn position-pair->distance
  "Calculates the distance between two position maps"
  [pos1 pos2]
  (haversine-distance (:latitude pos1)
                      (:longitude pos1)
                      (:latitude pos2)
                      (:longitude pos2)))

(defn positions->steps
  "Converts a list of position maps to a list of step maps"
  [positions]
  (let [sorted-positions (sort-positions positions)

        ;; distance between each position in kms:
        step-distances (map position-pair->distance
                            positions
                            (rest positions))

        ;; step distances converted to meters:
        step-distances (map #(* % 1000) step-distances)

        ;; duration between each position (in ms) and the next position:
        step-durations (map #(- (:timestamp %2) (:timestamp %))
                            sorted-positions
                            (rest sorted-positions))

        ;; step durations converted to seconds:
        step-durations (map #(/ % 1000) step-durations)

        ;; step speeds (in m/s) (ie, distance / duration) — but avoid divide-by-zero:
        step-speeds (map #(if (zero? (second %))
                            0
                            (/ (first %) (second %)))
                         (map vector step-distances step-durations))


        ;; datetime of each position (ie, start of step & end of step):
        start-datetimes (butlast (map :datetime positions))
        end-datetimes (rest (map :datetime positions))


        ;; start latitiude/longitude of each step:
        start-latitudes (butlast (map :latitude positions))
        start-longitudes (butlast (map :longitude positions))


        ;; end latitiude/longitude of each step: 
        end-latitudes (rest (map :latitude positions))
        end-longitudes (rest (map :longitude positions))]
    (map #(hash-map :distance (nth % 0)
                    :duration (nth % 1)
                    :speed (nth % 2)
                    :start-datetime (nth % 3)
                    :end-datetime (nth % 4)
                    :start-latitude (nth % 5)
                    :start-longitude (nth % 6)
                    :end-latitude (nth % 7)
                    :end-longitude (nth % 8))
          (map vector
               step-distances
               step-durations
               step-speeds
               start-datetimes
               end-datetimes
               start-latitudes
               start-longitudes
               end-latitudes
               end-longitudes))))

(defn get-work-address
  "Read the work address from a json file in iCloud"
  [path]
  (:work (json/parse-string (slurp path) true)))

(defn at-work?
  "Returns true if the given position map is within 250m of the work address"
  [position work-address]
  (let [distance (haversine-distance (:latitude position)
                                     (:longitude position)
                                     (:latitude work-address)
                                     (:longitude work-address))]
    (<= (* distance 1000) 250)))

(defn tag-work-positions
  "Tag each position map in a list of positions with a :work? key."
  [positions work-address]
  (map #(assoc % :work? (at-work? % work-address)) positions))

(defn add-date-to-positions
  "Add a :date key to each position map in a list of positions."
  [positions]
  (map #(assoc % :date (first (str/split (:datetime %) #"T"))) positions))

(defn -main
  [& args]
  (let [work-address (get-work-address
                      (fs/unixify
                       (fs/expand-home icloud-addresses-path)))
        files (list-json-files
               (fs/unixify
                (fs/expand-home icloud-log-dir)))
        positions (sort-positions
                   (add-date-to-positions
                    (tag-work-positions
                     (get-valid-positions files)
                     work-address)))
        steps (positions->steps positions)
        days (set (map :date positions))
        work-days (sort (into []
                              (set
                               (map :date
                                    (filter :work? positions)))))]
    (println work-days)))

(-main *command-line-args*)