#!/usr/bin/env bb
;;
;; A babashka script for logging the total number of words written in all files
;; in a given directory and outputting a score (based on that log) for the 
;; rolling average 'delta' in word count.
;;
;; Usage: ./logwords.bb --dir /path/to/directory --log /path/to/logfile --n 100

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
  ([dir extensions]
   (->> (fs/list-dir dir)
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
   (let [total-words (count-words-in-dir dir extensions)
         log-fp (fs/unixify (fs/expand-home log-fp))
         now (java.time.Instant/now)
         log-line (str (fs/unixify (fs/expand-home dir)) ", "
                       now ", "
                       total-words "\n")]
          (spit log-fp log-line :append true))))


(defn- read-log
  "Read the log of the total number of words as a list of integers. We expect a
   csv with a path, date, & word count on each line; only return word counts."
  [log-file]
  (if (fs/exists? log-file)
    (->> (slurp log-file)
         (str/split-lines)
         (map #(str/split % #","))
         (map last)
         (map str/trim)
         (filter #(re-matches #"\d+" %))
         (map #(Integer/parseInt %)))
    []))


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


(def cli-opts
  {:dir {:default nil
         :description "The directory to count words in."
         :parse-fn #(fs/unixify (fs/expand-home %))}
   :log {:default nil
         :description "The log file to write the total word count to."
         :parse-fn #(fs/unixify (fs/expand-home %))}
   :n {:default 100
       :description "The number of entries to keep in the log."
       :parse-fn #(Integer/parseInt %)}})

(defn -main
  ""
  []
  (let [{:keys [dir log n]} (cli/parse-opts *command-line-args* cli-opts)]
    (cond (nil? dir) (println "Please provide a directory to count words in.")
          (not (fs/exists? dir)) (println "Given directory does not exist.")
          (nil? log) (println "Provide a file to write the word count log to.")
          (not (fs/exists? log)) (spit log "")
          :else
          (do (log-total-word-count dir log)
              (println (int (ewma (calculate-deltas (read-log log)))))))))

(-main)