#!/usr/bin/env bb
;; =============================================================================
;; CONJOIN.BB - Audiobook MP3 Joiner
;; =============================================================================
;; Join, sort, and chapterize audiobook MP3 files using ffmpeg.
;;
;; Usage:
;;   conjoin.bb -i <input_dir> -o <output_dir>
;;   conjoin.bb --help
;;
;; Requires: ffmpeg, ffprobe
;; =============================================================================

(require '[babashka.fs :as fs]
         '[babashka.cli :as cli]
         '[clojure.java.shell :refer [sh]]
         '[clojure.string :as str])

(defn list-mp3s
  "List all mp3s in a given directory."
  [path]
  (let [files (fs/list-dir (fs/path path))]
    (filter #(= "mp3" (fs/extension %)) files)))

(defn has-mp3s?
  "Return true if a given directory contains mp3 files."
  [directory]
  (> (count (list-mp3s directory)) 1))

(defn list-audiobook-folders
  "List all folders in a given directory that look like they contain audiobooks"
  [path]
  (let [subdirs (filter fs/directory? (fs/list-dir (fs/path path)))]
    (filter has-mp3s? subdirs)))

(defn numeric-filenames?
  "Return true if a list of files has numbers in all the filenames (not counting the extension)."
  [l]
  (every? #(re-find #"\d+" %) (map #(fs/file-name %) l)))

(defn zero-padded?
  "Returns true if a list of files seems to contain zeropadded numbers in the filenames"
  [l]
  (some? (some #(re-find #"^0" %)
               (map #(re-find #"\d+" %)
                    (map #(fs/file-name %) l)))))

(defn sort-by-numeric
  "Sort a list of filenames by the numeric value contained within filenames."
  [l]
  (let [digits (map #(re-find #"\d+" %) (map #(fs/file-name %) l))
        numbers (map #(Integer/parseInt %) digits)]
    (map first (sort-by second (zipmap l numbers)))))

(defn sort-mp3-list
  "Sort a list of mp3 filepaths by likely order."
  [l]
  (if (numeric-filenames? l)
    (sort-by-numeric l)
    (sort l)))

(defn pad "Zero pad an integer."
  [i]
  (format "%06d" i))

(defn sort-and-copy
  "Copy all mp3s in a given directory to a new directory with sorted names,
   returning a map of new to original filenames."
  [in out]
  (let [files (sort-mp3-list (list-mp3s in))
        outpaths (map-indexed (fn [i v] (str (fs/path out)
                                             fs/file-separator
                                             (pad i)
                                             "."
                                             (fs/extension v)))
                              files)]
    (doall (map-indexed (fn [i v] (fs/copy v (nth outpaths i))) files))
    (zipmap outpaths files)))

(defn create-list-txt
  "Create 'list.txt' file in the outpath suitable for ffmpeg concatenate."
  [l outpath]
  (let [listpath (str outpath fs/file-separator "list.txt")
        text (str/join "\n" (map #(str "file '" (fs/expand-home %) "'") l))]
    (spit listpath text)))

(defn ffmpeg-join
  "Conjoin a list of mp3 files (declared in a list.txt file) by calling ffmpeg."
  [list-text-path outdir]
  (let [outfile (str outdir fs/file-separator "out.mp3")
        result (sh "ffmpeg" "-f" "concat" "-safe" "0" "-i" list-text-path outfile)]
    (str (:out result))))

(defn ffmpeg-downsample
  "Take a given mp3 file and downsample it to 32kbps."
  [inpath outpath]
  (str (:out (sh "ffmpeg" "-i" inpath "-b:a" "32k" outpath))))

(defn get-duration
  "Get the duration of a given file."
  [path]
  (let [result (sh "ffprobe" "-i" (fs/unixify path) "-show_entries" "format=duration" "-v" "quiet" "-of" "csv=p=0")]
    (str (:out result))))

(defn get-durations
  "Get the durations of all mp3s in a given directory."
  [path]
  (map #(get-duration %) (sort-mp3-list (list-mp3s path))))

(defn clean-chapter-title
  "Remove any *leading* digits/hyphens/whitespace from a chapter title, leaving
    only the chapter title itself.
   If the cleaned title is empty, just return the original title."
  [s]
  (let [leading-digits #"^[\d\-\s]*"
        ;; remove leading digits/hyphens/whitespace
        cleaned (str/replace-first s leading-digits "")]
    (if (empty? cleaned)
      s
      cleaned)))

(defn create-metadata-txt
  "Generate a txt file (in the same dir as the mp3s) with chaptered metadata."
  [path file-mapping]
  (let [mp3s (sort-mp3-list (list-mp3s path))
        durations (get-durations path)
        titles (map #(clean-chapter-title (str (fs/strip-ext (fs/file-name (get file-mapping (str (fs/unixify %)))))))
                    mp3s)
        cumulative-durations (map int (reductions + (map #(Float/parseFloat %) durations)))
        starting-times (cons 0 (butlast cumulative-durations))
        ending-times (map-indexed (fn [i v]
                                    (if (= i (dec (count cumulative-durations)))
                                      v
                                      (- v 1)))
                                  cumulative-durations)]
    (spit (str path fs/file-separator "metadata.txt")
          (str ";FFMETADATA1\n\n"
               (str/join "\n"
                         (map-indexed (fn [i v]
                                        (str "[CHAPTER]"
                                             "\n"
                                             "TIMEBASE=1/1"
                                             "\n"
                                             (str "START=" (nth starting-times i))
                                             "\n"
                                             (str "END=" (nth ending-times i))
                                             "\n"
                                             (str "title=" (nth titles i))
                                             "\n"))
                                      mp3s))))))

(defn ffmpeg-chapterise
  "Chapterise a given mp3 file using metadata.txt."
  [metadata-path inpath outpath]
  (str (:out (sh "ffmpeg" "-i" inpath "-i" metadata-path "-map_metadata" "1" "-codec" "copy" outpath))))

(defn conjoin
  "Run the conjoining process."
  [inpath outpath]
  (let [tempdir (fs/create-temp-dir)
        file-mapping (sort-and-copy inpath tempdir)
        temp-files (sort-by-numeric (keys file-mapping))]
    ;; (1) SORT AND COPY
    (println (str "Sorting and copying mp3s found in " inpath "..."))
    (doseq [temp-f temp-files]
      (println (str "\"" (fs/file-name (get file-mapping temp-f)) "\" -> " temp-f)))

    ;; (2) CREATE LIST.TXT FOR CONCATENATION
    (println (str "Creating list.txt file in " tempdir "..."))
    (create-list-txt (sort-mp3-list (list-mp3s tempdir)) tempdir)
    (println (str "List file:\n"
                  (slurp (str tempdir fs/file-separator "list.txt"))))

    ;; (3) CREATE CHAPTERED METADATA
    (println (str "Creating metadata.txt file in " tempdir "..."))
    (create-metadata-txt tempdir file-mapping)
    (println (str "Metadata file:\n"
                  (slurp (str tempdir fs/file-separator "metadata.txt"))))

    ;; (4) JOIN
    (println (str "Joining mp3s in " tempdir "..."))
    (ffmpeg-join (str tempdir fs/file-separator "list.txt") outpath)
    (println (str "Joined file: " outpath fs/file-separator "out.mp3"))

    ;; (5) CHAPTERISE JOINED FILE (USING METADATA)
    (println (str "Chapterising " outpath fs/file-separator "out.mp3..."))
    (ffmpeg-chapterise (str tempdir fs/file-separator "metadata.txt")
                       (str outpath fs/file-separator "out.mp3")
                       (str outpath fs/file-separator "out_chaptered.mp3"))
    (println (str "Chapterised file: " outpath fs/file-separator "out_chaptered.mp3"))

    ;; (6) CLEANUP TEMP DIR
    (println (str "Deleting temporary directory " tempdir "..."))
    (fs/delete-tree tempdir)

    ;; (7) DOWNSAMPLE
    (println (str "Downsampling " outpath fs/file-separator "out_chaptered.mp3..."))
    (ffmpeg-downsample (str outpath fs/file-separator "out_chaptered.mp3")
                       (str outpath fs/file-separator "out_chaptered_small.mp3"))

    ;; (8) DELETE ORIGINALS
    (println (str "Deleting " outpath fs/file-separator "out.mp3..."))
    (fs/delete (str outpath fs/file-separator "out.mp3"))
    (println (str "Deleting " outpath fs/file-separator "out_chaptered.mp3..."))
    (fs/delete (str outpath fs/file-separator "out_chaptered.mp3"))

    ;; (9) RENAME
    (println (str "Renaming " outpath fs/file-separator
                  "out_chaptered_small.mp3 to " (fs/file-name inpath) ".mp3"))
    (fs/move (str outpath fs/file-separator "out_chaptered_small.mp3")
             (str outpath fs/file-separator (fs/file-name inpath) ".mp3"))))

(def cli-spec {:input  {:ref          "<directory>"
                        :desc         "The input directory (which contains mp3s)."
                        :alias        :i
                        :default      nil}
               :output {:ref          "<directory>"
                        :desc         "The output directory (to save a joined mp3)."
                        :alias        :o
                        :default      nil}
               :help   {:desc         "Show this help message."
                        :alias        :h
                        :coerce       :boolean}})

(defn print-help
  "Print help message and usage information."
  []
  (println "conjoin.bb - Audiobook MP3 Joiner")
  (println "")
  (println "Join, sort, and chapterize audiobook MP3 files using ffmpeg.")
  (println "")
  (println "Usage:")
  (println "  conjoin.bb -i <input_dir> -o <output_dir>")
  (println "  conjoin.bb --help")
  (println "")
  (println "Options:")
  (println "  -i, --input <dir>   Input directory containing MP3 files")
  (println "  -o, --output <dir>  Output directory for joined audiobook")
  (println "  -h, --help          Show this help message")
  (println "")
  (println "Process:")
  (println "  1. Sorts MP3s by numeric filename order")
  (println "  2. Creates chapter metadata from filenames")
  (println "  3. Joins files with ffmpeg")
  (println "  4. Adds chapter markers")
  (println "  5. Downsamples to 32kbps for smaller file size")
  (println "")
  (println "Requires: ffmpeg, ffprobe"))

(defn -main
  "Run the conjoining process."
  [& args]
  (let [{:keys [opts]} (cli/parse-args args {:spec cli-spec})
        inpath (:input opts)
        outpath (:output opts)]
    (cond
      (:help opts)
      (print-help)

      (and inpath outpath)
      (if (and (fs/exists? (fs/expand-home inpath))
               (has-mp3s? (fs/expand-home inpath))
               (fs/exists? (fs/expand-home outpath)))
        (conjoin (fs/expand-home inpath) (fs/expand-home outpath))
        (println "Input or output directory does not exist or does not contain mp3s."))

      :else
      (do
        (println "Usage: conjoin.bb -i <input dir> -o <output dir>")
        (println "")
        (println "Run 'conjoin.bb --help' for more information.")))))

(apply -main *command-line-args*)
