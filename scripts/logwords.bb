#!/usr/bin/env bb
;;
;; A babashka script for logging the total number of words written in all files
;; in a given directory and then outputting a brief report on the change in
;; word count: the total number of words written in the last 24 hours, and the
;; recent change in word count (calculated as the minimum of the exponentially
;; weighted moving average and the average of the logged deltas).
;;
;; Usage: ./logwords.bb --dir /path/to/directory --log /path/to/logfile --n 100
;;
;; Note that an optional --flat flag is used to prevent the script from finding
;; and counting words in subdirectories.

(require '[babashka.fs :as fs]
         '[clojure.string :as str]
         '[babashka.cli :as cli])


(defn- count-words-in-file
  "Count the number of words in a file."
  [file]
  (-> (slurp file)
      (str/split #"\s+")
      count))


(defn- has-extension?
  "Check if a file has one of the given extensions."
  [file extensions]
  (let [ext (fs/extension file)]
    (some #(= ext %) extensions)))


(defn- count-words-in-dir
  "Get the total number of words in all files in a directory, filtered to only
   include files with the given extensions."
  ([dir] (count-words-in-dir dir ["txt" "md"]))
  ([dir extensions] (count-words-in-dir dir extensions false))
  ([dir extensions flat?]
   (->> (if flat? (fs/list-dir dir) (fs/glob dir "**"))
        (filter #(has-extension? % extensions))
        (map #(fs/expand-home %))
        (map #(fs/unixify %))
        (map count-words-in-file)
        (reduce +))))


(defn- log-total-word-count
  "Log a new total the total number of words in a directory."
  ([dir log-fp]
   (log-total-word-count dir log-fp ["txt" "md"]))
  ([dir log-fp extensions]
   (log-total-word-count dir log-fp extensions false))
  ([dir log-fp extensions flat?]
   (let [total-words (count-words-in-dir dir extensions flat?)]
     (when (pos? total-words)  ; Only log if word count is positive
       (let [log-fp (fs/unixify (fs/expand-home log-fp))
             now (java.time.Instant/now)
             log-line (str (fs/unixify (fs/expand-home dir)) ", "
                           now ", "
                           total-words "\n")]
         (spit log-fp log-line :append true))))))


(defn- seconds-since
  "Get the number of seconds between now and a given date.

   date will be a string in the format 'yyyy-MM-ddTHH:mm:ssZ'
      eg: '2024-09-07T03:39:01.124108Z'"
  [date]
  (let [now (java.time.Instant/now)
        date (java.time.Instant/parse (str/trim date))
        seconds (-> date
                    (.until now java.time.temporal.ChronoUnit/SECONDS)
                    int)]
    (-> date
        (.until now java.time.temporal.ChronoUnit/SECONDS)
        int)))

(defn- seconds-since-local-midnight
  "Get the number of seconds between now and the start of the current *local*
   system day."
  []
  (let [utc-now (java.time.Instant/now)
        local-now (.atZone utc-now (java.time.ZoneId/systemDefault))
        start-of-day (.withSecond (.withMinute (.withHour local-now 0) 0) 0)]
    ;; get the number of seconds between the local now and the local start of day
    (-> start-of-day
        (.until local-now java.time.temporal.ChronoUnit/SECONDS)
        int)))


(defn- read-log
  "Read the log of the total number of words as a list of integers. We expect a
   csv with a path, date, & word count on each line; only return word counts.
   
   Optionally filter for only lines in the last s seconds; default is s=nil.
   "
  ([log-file] (read-log log-file nil))
  ([log-file s]
   (let [is-recent #(if s (<= (seconds-since %) s) true)]
     (if (fs/exists? log-file)
       (->> (slurp log-file) ;; Read the log file
            (str/split-lines) ;; Split on newlines
            (rest) ;; Skip the header line
            (map #(str/split % #",")) ;; Split on commas
            (filter #(= (count %) 3)) ;; Require 3 elements (path, date, wc)
            (filter #(is-recent (second %))) ;; Require newer than s seconds
            (map last) ;; Get the word count
            (map str/trim) ;; Trim whitespace
            (filter #(re-matches #"\d+" %)) ;; Filter out non-numeric entries
            (map #(Integer/parseInt %))) ;; Convert word counts to integers
       []))))


(defn- trim-log
  "Trim log file (given by the path 'log-file') to the most recent n entries.
  Keep the header line!"
  ([log-file]
   (trim-log log-file 100))
  ([log-file n]
   (let [header (->> (slurp log-file)
                     (str/split-lines)
                     (first)
                     (str/trim))
         lines (->> (slurp log-file)
                    (str/split-lines)
                    (rest) ;; Skip the header line
                    (take-last n))
         lines (if (empty? lines) [header] (cons header lines))]
     (->> lines
          (str/join "\n")
          (str/trim)
          (#(str % "\n"))
          (spit log-file)))))


(defn- calculate-deltas
  "Get the delta between each entry in the log."
  [log]
  (map - (map - log (rest log))))


(defn- avg
  "Get the average of a list of numbers."
  [nums]
  (/ (reduce + nums) (count nums)))


(defn- ewma
  "Calculate the exponentially weighted moving average of a list of numbers."
  ([nums] (ewma nums 0.1))
  ([nums alpha]
   (let [alpha 0.1]
     (reduce (fn [acc x]
               (+ (* alpha x) (* (- 1 alpha) acc)))
             nums))))


(defn- date->local
  "Convert a date string to a local date string."
  [date]
  (let [date (java.time.Instant/parse (str/trim date))
        zone (java.time.ZoneId/systemDefault)
        local-date (.atZone date zone)]
    (.format local-date java.time.format.DateTimeFormatter/ISO_LOCAL_DATE)))


(defn- generate-report
  "Read the log file and return a report on per-day changes."
  [log-file]
  (if (fs/exists? log-file)
    (let [local-log (->> (slurp log-file)
                         (str/split-lines)
                         (rest)
                         (map #(str/split % #","))
                         (filter #(= (count %) 3))
                         (map #(map str/trim %))
                         (map #(vector (second %) (date->local (second %)) (last %))))
          days (distinct (map second local-log))]
      (->> days
           (map (fn [date]
                  (let [entries (filter #(= date (second %)) local-log)
                        wcs (->> entries
                                 (map last)
                                 (map str/trim)
                                 (filter #(re-matches #"\d+" %))
                                 (map #(Integer/parseInt %)))
                        deltas (calculate-deltas wcs)
                        first-wc (first wcs)
                        last-wc (last wcs)
                        net-change (- last-wc first-wc)]
                    (str date
                         " --- "
                         first-wc " -> " last-wc
                         " --- "
                         "↑" (count (filter pos? deltas))
                         " "
                         "↓" (count (filter neg? deltas))
                         " --- "
                         (if (pos? net-change)
                           (str "+" net-change)
                           (str net-change))))))
           (str/join "\n")))
    "No log file found."))


(def cli-opts
  {:dir {:default nil
         :description "The directory to count words in."
         :parse-fn #(fs/unixify (fs/expand-home %))}
   :log {:default nil
         :description "The log file to write the total word count to."
         :parse-fn #(fs/unixify (fs/expand-home %))}
   :n {:default (* 14 24 60 60) ;; 14 days by default, assuming 1 log per min
       :description "The number of entries to keep in the log."
       :parse-fn #(Integer/parseInt %)}
   :flat {:default false
          :description "Do not count words in subdirectories."
          :parse-fn #(= % "true")}
   :report {:default false
            :description "Print a report on the log file."
            :parse-fn #(= % "true")}})


(defn -main
  "Count the number of words in a target directory, log the total word count to
   a target log file, then print a report on the change in word count since
   midnight of the current day. If 'n' is provided, log file will be trimmed
   to the most recent n entries."
  []
  (let [{:keys [dir log n report flat]} (cli/parse-opts *command-line-args* cli-opts)]
    (cond (nil? dir) (println "Please provide a directory to count words in.")
          (not (fs/exists? dir)) (println "Given directory does not exist.")
          (nil? log) (println "Provide a file to write the word count log to.")
          (not (fs/exists? log)) (spit log "\"path\",\"date\",\"wordcount\"\n")
          (= true report) (println (generate-report log))
          :else (do (log-total-word-count dir log ["txt" "md"] flat)
                    (trim-log log (or n (get-in cli-opts [:n :default])))
                    (let [deltas (->> (read-log log) (calculate-deltas))
                          change (int (avg [(ewma deltas) (last deltas)]))
                          change (if (pos? change)
                                   (str "+" change)
                                   (str change))
                          wc-now (last (read-log log))
                          ;; wc-24-hours-ago (first (read-log log (* 24 60 60)))
                          ;; in-last-24h (- wc-now wc-24-hours-ago) 
                          wc-at-midnight (first (read-log log (+ (seconds-since-local-midnight) 60)))
                          wc-since-midnight (- wc-now wc-at-midnight)]
                      (println (str wc-since-midnight
                                    " (" change ")")))))))

(-main)