#!/usr/bin/env bb
(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)

(defn expand-home [s]
  (if (.startsWith s "~")
    (clojure.string/replace-first s "~" (System/getProperty "user.home"))
    s))

(def outpath (expand-home "~/Documents/blog/data/logs/mood.csv"))

(def now
  ; formatted current time
  (.format (LocalDateTime/now)
           (DateTimeFormatter/ofPattern "dd-MMM-yyyy HH:mm")))

(def mood
  ; the first number 
  (Integer/parseInt (re-find #"\d+" (str/join " " *command-line-args*))))


(if (.exists (io/file outpath))
  (spit outpath
        (str/join [(str/join "," [now mood]) "\n"])
        :append true))