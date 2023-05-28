#!/usr/bin/env bb
;; A script for counting lines of clojure code in a given directory (& subdirs).
;; Also counts lines of edn and babashka scripts.
;; Usage: loc.bb <dir>

(require '[clojure.string :as str]
         '[babashka.fs :as fs])

(def extensions ["clj" "cljs" "cljc" "edn" "bb"])
(def comment-chars [";"])

(defn count-lines
  "Count the number of non-empty (and non-comment) lines in a file."
  ([file]
   (count-lines file comment-chars))
  ([file comment-characters]
   (->> (slurp file)
        (str/split-lines) ;; split into lines
        (map str/trim) ;; remove leading and trailing whitespace
        (filter #(seq %)) ;; remove empty lines
        ; filter out all lines that start with a comment character:
        (filter #(not (some (fn [c] (str/starts-with? % c)) comment-characters)))
        (count))))

(defn valid-file?
  "Returns true if the file has the necessary extension."
  ([file]
   (valid-file? file extensions))
  ([file extensions]
   (let [ext (-> file
                 (str)
                 (str/split #"\.")
                 (last))]
     (some #(= ext %) extensions))))

(defn -main [& args]
  (let [path (fs/expand-home (first (first args)))]
    (when (not (fs/exists? path))
      (println "The given path does not exist.")
      (System/exit 1))
    (if (fs/regular-file? path)
      (if (valid-file? path)
        (println (count-lines (fs/unixify path)))
        (println "The given file is not a valid file."))
      (let [files (fs/glob path "**")
            valid-files (filter valid-file? files)
            line-counts (map #(count-lines (fs/unixify %)) valid-files)
            total (reduce + line-counts)]
        (println total)))))

(-main *command-line-args*)