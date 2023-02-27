#!/usr/bin/env bb
; A script for logging net fiat balance (according to bank)

(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)

(defn expand-home [s] (str/replace-first s "~" (System/getProperty "user.home")))

(def outpath (expand-home "~/Documents/blog/data/logs/fiat.csv"))

(def currency "AUD")

(def now
  ; formatted current time
  (.format (LocalDateTime/now)
           (DateTimeFormatter/ofPattern "dd-MMM-yyyy HH:mm")))

(def rawnum
  ; read *command-line-args* into a signed number
  (Float/parseFloat (str/replace (str/join "" *command-line-args*) "," "")))

(def cleannum
    ; convert the number to a string (with a $)
    ; if the number is negative, add a - sign before the $
    (if (neg? rawnum)
        (str "-$" (Math/abs rawnum))
        (str "$" rawnum)))

(if (io/file outpath)
  (spit outpath
        (str/join [(str/join ", " [now currency cleannum]) "\n"])
        :append true))
