#!/usr/bin/env bb
; A script for logging net fiat balance (according to bank)

(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)

(require '[babashka.process :as p])

(defn expand-home [s] (str/replace-first s "~" (System/getProperty "user.home")))

(defn run-applescript [script-file arg]
  (-> (p/process ["osascript" script-file arg])
      deref
      :exit))

(def outpath (expand-home "~/Documents/blog/data/logs/fiat.csv"))

(def applescript-file
  (expand-home "~/.dotfiles/applescripts/mark_task_complete.scpt"))

(def now
  ; formatted current time
  (.format (LocalDateTime/now)
           (DateTimeFormatter/ofPattern "dd-MMM-yyyy HH:mm")))

(defn -main
  "Log fiat from cmdlinearg input"
  [cmd-line-args]
  (let [currency "AUD"
        rawnum (Float/parseFloat
                 (str/replace (str/join "" cmd-line-args) "," ""))
        cleannum (if (neg? rawnum)
                   (str "-$" (Math/abs rawnum))
                   (str "$" rawnum))]
    (if (io/file outpath)
      (spit outpath
            (str/join [(str/join ", " [now currency cleannum]) "\n"])
            :append true))
    (run-applescript applescript-file "Log fiat net worth")))

(-main *command-line-args*)
