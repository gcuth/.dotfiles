#!/usr/bin/env bb
(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)

(require '[babashka.process :as p])

(defn expand-home [s]
  (if (.startsWith s "~")
    (clojure.string/replace-first s "~" (System/getProperty "user.home"))
    s))

(def outpath (expand-home "~/Documents/blog/data/logs/mood.csv"))

(def applescript-file
  (expand-home "~/.dotfiles/applescripts/mark_task_complete.scpt"))

(def now
  ; formatted current time
  (.format (LocalDateTime/now)
           (DateTimeFormatter/ofPattern "dd-MMM-yyyy HH:mm")))

(defn run-applescript [script-file arg]
  (-> (p/process ["osascript" script-file arg])
      deref
      :exit))

(defn -main
  "Run the actual mood-logging process"
  [cmd-line-args]
  (let [mood (Integer/parseInt (re-find #"\d+" (str/join " " cmd-line-args)))]
     (if (.exists (io/file outpath))
       (spit outpath (str/join [(str/join "," [now mood]) "\n"])
             :append true))
     (run-applescript applescript-file "Log mood")))

(-main *command-line-args*)
