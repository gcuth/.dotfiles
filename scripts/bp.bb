#!/usr/bin/env bb
(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)

(require '[babashka.process :as p])

(defn expand-home [s]
  (if (.startsWith s "~")
    (clojure.string/replace-first s "~" (System/getProperty "user.home"))
    s))

(defn run-applescript [script-file arg]
  (-> (p/process ["osascript" script-file arg])
      deref
      :exit))

(def outpath (expand-home "~/Documents/blog/data/logs/bp.csv"))

(def applescript-file
  (expand-home "~/.dotfiles/applescripts/mark_task_complete.scpt"))

(def now
  ; formatted current time
  (.format (LocalDateTime/now)
           (DateTimeFormatter/ofPattern "dd-MMM-yyyy HH:mm")))

(def rawnums 
  ; raw numbers input into cli, divided by space or /
  (map #(Integer/parseInt (re-find #"\d+" %))
       (str/split (str/join " " *command-line-args*) #"\/| ")))

(def systolic (reduce max rawnums))
(def diastolic (reduce min rawnums))

(do
    (if (and (= (count rawnums) 2) (.exists (io/file outpath))) 
      (spit outpath
            (str/join [(str/join "," [now (float diastolic) (float systolic)]) "\n"])
            :append true))
    (run-applescript applescript-file "Log blood pressure")
        )
