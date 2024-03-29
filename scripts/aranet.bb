#!/usr/bin/env bb
;;
;; A small babashka script which wraps around the 'claranet4' command line tool
;; Checks for a valid 'claranet4' executable in path, then runs it to read any
;; available Aranet devices. Gets the current status, injects the datetime, and
;; saves the data to a json file at the outpath.

(require '[babashka.fs :as fs]
         '[clojure.string :as s]
         '[babashka.process :as sh]
         '[cheshire.core :as json])

(def OUTPATH (fs/unixify (fs/expand-home "~/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/Logs/Aranet4/Snapshots/")))
(def CLARANETPATH (or (fs/unixify (fs/which "claranet4"))
                      "/opt/homebrew/bin/claranet4"))

(defn get-snapshot
  "Get the raw data from the 'claranet4' command line tool."
  []
  (:out (sh/sh CLARANETPATH "read" "--quiet")))

(defn generate-filename
  "Given a snapshot, generate a filename for the json file.
   Should be of the form YYYY-MM-DD-HH-MM-SS-[Aranet-Device-Name].json"
  [snapshot]
  (let [date (str (java.time.LocalDate/now))
        time (first (s/split (s/replace
                              (str (java.time.LocalTime/now)) ":" "-")
                             #"\."))
        device-name (s/replace (:name snapshot) #" " "-")]
    (s/lower-case (str date "-" time "-" device-name ".json"))))

(defn -main
  "Run the snapshotting process, using the directory given in the first arg as
   an outpath for the json file. If no outpath is given, use the default.
   "
  []
  (println (str (java.time.LocalDateTime/now)) "---" "Getting aranet data ...")
  (println (str (java.time.LocalDateTime/now)) "---" "Using claranet:" CLARANETPATH)
  (let [outpath (if (empty? *command-line-args*)
                  OUTPATH
                  (fs/unixify (fs/expand-home (first *command-line-args*))))
        now (str (java.time.LocalDateTime/now))
        raw-snapshot (get-snapshot)
        parsed (json/parse-string raw-snapshot true)
        to-output (assoc parsed :datetime now)
        filename (generate-filename to-output)
        outpath (str outpath "/" filename)]
    (println (str (java.time.LocalDateTime/now)) "---" "Using outpath:" outpath)
    (spit outpath (json/generate-string to-output {:pretty true}))
    (println (str (java.time.LocalDateTime/now)) "---" "Saved snapshot to" outpath)))


(-main)