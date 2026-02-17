#!/usr/bin/env bb
;; =============================================================================
;; LOGWORDS.BB - Writing Productivity Tracker
;; =============================================================================
;; Tracks daily word counts with streak tracking and goal monitoring.
;;
;; Features:
;;   - Counts words in text/markdown files
;;   - Tracks daily progress toward configurable goal
;;   - Maintains writing streak (consecutive days meeting goal)
;;   - Calculates exponentially weighted moving average (EWMA) of changes
;;   - Outputs concise progress for menu bar display (One Thing)
;;
;; Usage:
;;   logwords.bb --dir <path> --log <path>     Track words and show progress
;;   logwords.bb --report --log <path>         Show detailed daily report
;;   logwords.bb --streak --log <path>         Show streak information
;;   logwords.bb --help                        Show help
;;
;; Output format (default):
;;   "523 (+12 | 45230)"  = today's words (change | total)
;;   "523 🔥3 (+12)"      = with streak display (--show-streak)
;;
;; =============================================================================

(require '[babashka.fs :as fs]
         '[clojure.string :as str]
         '[babashka.cli :as cli]
         '[cheshire.core :as json])

;; -----------------------------------------------------------------------------
;; Configuration
;; -----------------------------------------------------------------------------

(def ^:private config-path
  "Path to the shared productivity config file."
  (str (fs/expand-home "~/.dotfiles/config/productivity.json")))

(defn- load-config
  "Load configuration from the productivity.json file."
  []
  (if (fs/exists? config-path)
    (try
      (json/parse-string (slurp config-path) true)
      (catch Exception _
        {}))
    {}))

(defn- get-default-goal
  "Get the default daily goal from config, or 1000 if not configured."
  []
  (get-in (load-config) [:writing :dailyGoal] 1000))

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

(defn- today-local
  "Get today's date as a local date string (YYYY-MM-DD)."
  []
  (let [zone (java.time.ZoneId/systemDefault)
        now (java.time.LocalDate/now zone)]
    (.format now java.time.format.DateTimeFormatter/ISO_LOCAL_DATE)))

(defn- yesterday-local
  "Get yesterday's date as a local date string (YYYY-MM-DD)."
  []
  (let [zone (java.time.ZoneId/systemDefault)
        yesterday (.minusDays (java.time.LocalDate/now zone) 1)]
    (.format yesterday java.time.format.DateTimeFormatter/ISO_LOCAL_DATE)))

(defn- days-ago-local
  "Get the date n days ago as a local date string (YYYY-MM-DD)."
  [n]
  (let [zone (java.time.ZoneId/systemDefault)
        date (.minusDays (java.time.LocalDate/now zone) n)]
    (.format date java.time.format.DateTimeFormatter/ISO_LOCAL_DATE)))

(defn- get-daily-totals
  "Read the log file and return a map of date -> daily word count change.
   Returns a map like {\"2024-01-15\" 523, \"2024-01-14\" 892, ...}"
  [log-file]
  (if (fs/exists? log-file)
    (let [local-log (->> (slurp log-file)
                         (str/split-lines)
                         (rest)
                         (map #(str/split % #","))
                         (filter #(= (count %) 3))
                         (map #(map str/trim %))
                         (map #(vector (date->local (second %)) (last %))))]
      (->> (distinct (map first local-log))
           (map (fn [date]
                  (let [entries (filter #(= date (first %)) local-log)
                        wcs (->> entries
                                 (map second)
                                 (map str/trim)
                                 (filter #(re-matches #"\d+" %))
                                 (map #(Integer/parseInt %)))
                        first-wc (first wcs)
                        last-wc (last wcs)
                        net-change (if (and first-wc last-wc)
                                     (- last-wc first-wc)
                                     0)]
                    [date net-change])))
           (into {})))
    {}))

(defn- calculate-streak
  "Calculate the current writing streak (consecutive days meeting goal).
   Returns a map with :current, :longest, and :met-today? keys."
  [log-file goal]
  (let [daily-totals (get-daily-totals log-file)
        today (today-local)
        today-words (get daily-totals today 0)
        met-today? (>= today-words goal)]
    ;; Count consecutive days meeting goal, starting from yesterday
    ;; (today counts separately since it might still be in progress)
    (loop [day-offset 1
           streak (if met-today? 1 0)
           longest-streak (if met-today? 1 0)]
      (let [check-date (days-ago-local day-offset)
            day-words (get daily-totals check-date 0)]
        (if (>= day-words goal)
          ;; Continue the streak
          (recur (inc day-offset)
                 (inc streak)
                 (max longest-streak (inc streak)))
          ;; Streak broken - but check if we should continue counting for longest
          {:current streak
           :met-today? met-today?
           :today-words today-words
           :goal goal
           :longest longest-streak})))))

(defn- generate-streak-report
  "Generate a detailed streak report."
  [log-file goal]
  (let [{:keys [current met-today? today-words longest]} (calculate-streak log-file goal)
        daily-totals (get-daily-totals log-file)
        today (today-local)]
    (str "Writing Streak Report\n"
         "=====================\n"
         "\n"
         "Daily Goal:     " goal " words\n"
         "Today's Words:  " today-words (if met-today? " ✓" " (in progress)") "\n"
         "Current Streak: " current " day" (if (not= current 1) "s" "") "\n"
         "\n"
         "Recent Days:\n"
         (->> (range 7)
              (map (fn [n]
                     (let [date (days-ago-local n)
                           words (get daily-totals date 0)
                           met? (>= words goal)
                           label (cond (= n 0) "Today"
                                       (= n 1) "Yesterday"
                                       :else date)]
                       (str "  " label ": " words " words"
                            (if met? " ✓" "")))))
              (str/join "\n")))))

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
            :parse-fn #(= % "true")}
   :streak {:default false
            :description "Show streak information."
            :parse-fn #(= % "true")}
   :goal {:default nil  ;; Will use config file or 1000
          :description "Daily word count goal for streak tracking."
          :parse-fn #(Integer/parseInt %)}
   :show-streak {:default false
                 :description "Include streak in default output (for menu bar)."
                 :parse-fn #(= % "true")}
   :help {:default false
          :description "Show help message."
          :parse-fn #(= % "true")}})

(defn print-help
  "Print help message."
  []
  (println "logwords.bb - Writing Productivity Tracker")
  (println "")
  (println "Track daily word counts with streak tracking and goal monitoring.")
  (println "")
  (println "Usage:")
  (println "  logwords.bb --dir <path> --log <path>   Track words and show progress")
  (println "  logwords.bb --report --log <path>       Show detailed daily report")
  (println "  logwords.bb --streak --log <path>       Show streak information")
  (println "  logwords.bb --help                      Show this help message")
  (println "")
  (println "Options:")
  (println "  --dir <path>       Directory to count words in (required)")
  (println "  --log <path>       Log file path (required)")
  (println "  --goal <n>         Daily word count goal (default: from config or 1000)")
  (println "  --show-streak      Include streak emoji in output")
  (println "  --streak           Show detailed streak report")
  (println "  --report           Show detailed daily report")
  (println "  --flat             Don't count subdirectories")
  (println "  -n <n>             Number of log entries to keep")
  (println "  --help             Show this help message")
  (println "")
  (println "Output Formats:")
  (println "  Default:     \"523 (+12 | 45230)\"   today's words (change | total)")
  (println "  With streak: \"523 🔥3 (+12)\"        includes streak count")
  (println "")
  (println "Examples:")
  (println "  logwords.bb --dir ~/Notes --log ~/Logs/words.log")
  (println "  logwords.bb --dir ~/Notes --log ~/Logs/words.log --goal 500 --show-streak")
  (println "  logwords.bb --streak --log ~/Logs/words.log --goal 1000"))

(defn -main
  "Count the number of words in a target directory, log the total word count to
   a target log file, then print a report on the change in word count since
   midnight of the current day. If 'n' is provided, log file will be trimmed
   to the most recent n entries."
  []
  (let [{:keys [dir log n report flat streak goal show-streak help]}
        (cli/parse-opts *command-line-args* cli-opts)
        ;; Use provided goal, or fall back to config file, or default to 1000
        effective-goal (or goal (get-default-goal))]
    (cond
      ;; Help
      (= true help)
      (print-help)

      ;; Streak report (only needs log file)
      (= true streak)
      (if (and log (fs/exists? log))
        (println (generate-streak-report log effective-goal))
        (println "Please provide a valid log file with --log"))

      ;; Report (only needs log file)
      (= true report)
      (if (and log (fs/exists? log))
        (println (generate-report log))
        (println "Please provide a valid log file with --log"))

      ;; Standard operation - needs dir and log
      (nil? dir)
      (println "Please provide a directory to count words in. Use --help for usage.")

      (not (fs/exists? dir))
      (println "Given directory does not exist.")

      (nil? log)
      (println "Provide a file to write the word count log to. Use --help for usage.")

      ;; Create log file if it doesn't exist
      (not (fs/exists? log))
      (do
        (spit log "\"path\",\"date\",\"wordcount\"\n")
        (println "Created new log file. Run again to start tracking."))

      ;; Main tracking logic
      :else
      (do
        (log-total-word-count dir log ["txt" "md"] flat)
        (trim-log log (or n (get-in cli-opts [:n :default])))
        (let [deltas (->> (read-log log) (calculate-deltas))
              change (int (avg [(ewma deltas) (last deltas)]))
              change-str (if (pos? change)
                           (str "+" change)
                           (str change))
              wc-now (last (read-log log))
              wc-at-midnight (first (read-log log (+ (seconds-since-local-midnight) 60)))
              wc-since-midnight (- wc-now wc-at-midnight)
              ;; Calculate streak if showing
              streak-info (when show-streak (calculate-streak log effective-goal))
              streak-display (when (and show-streak
                                        streak-info
                                        (pos? (:current streak-info)))
                               (str " 🔥" (:current streak-info)))]
          ;; Output format depends on show-streak flag
          (if show-streak
            (println (str wc-since-midnight
                          (or streak-display "")
                          " (" change-str ")"))
            (println (str wc-since-midnight))))))))

(-main)
