#!/usr/bin/env bb
;; Find audio files somewhere (such as an external drive) & copy all of them
;; to a flat list of files in a target directory.
;; Usage: bb grab_external_audio.bb <source> <target-directory>

(require '[babashka.fs :as fs]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(def audio-extensions #{"mp3" "wav" "flac" "aac" "ogg" "m4a"})

(defn audio-file?
  "Returns true if the file is an audio file."
  [file]
  (contains? audio-extensions (fs/extension file)))

(defn find-audio-files
  "Returns a list of all audio files in a directory & subdirectories."
  [dir]
  (let [files (fs/glob dir "**")]
    (filter audio-file? files)))

(defn -main
  "Run the main program.
   
   If the second arg is present & a drive, copy audio files from that drive to
   the target directory.

   If no second arg is present, find all external drives, ask the user which
   one to copy from, then copy audio files from drive to the target directory."
  [& args]
  ;; Check for correct number of args & that source & target directories exist
  ;; before proceeding. If not, print usage message & exit.
  (cond (or (empty? args) (not= 2 (count args)))
        (do
          (println "Usage: ./grab_external_audio.bb <source> <target-dir>")
          (System/exit 1))
        (not (fs/directory? (first args)))
        (do
          (println "Source directory does not exist.")
          (System/exit 1))
        (not (fs/directory? (second args)))
        (do
          (println "Target directory does not exist.")
          (System/exit 1))
        :else
        (println (str "Copying audio files from \""
                      (first args)
                      "\" to \""
                      (second args)
                      "\" ...")))
  (let [source (-> (first args) (fs/expand-home) (fs/absolutize) (fs/unixify))
        target (-> (second args) (fs/expand-home) (fs/absolutize) (fs/unixify))
        audio-files (find-audio-files source)]
    ;; Copy audio files to target directory
    (doseq [file audio-files]
      (let [file-name (fs/file-name file)
            target (fs/unixify (str target fs/file-separator file-name))]
        (println (str file " -> " target "/" file-name))
        (if (fs/exists? target)
          (println (str "File already exists in target directory: " target))
          (fs/copy file target))))))


(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))