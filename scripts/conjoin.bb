#!/usr/bin/env bb
;
; a script for finding, sorting, joining, and outputting audiobooks in using
; ffmpeg.

(require '[babashka.fs :as fs])
(require '[babashka.cli :as cli])
(require '[clojure.java.shell :refer [sh]])

(def TESTINPATH (fs/expand-home "~/Downloads/Gravity's Rainbow/"))
(def TESTOUTPATH (fs/expand-home "~/Desktop"))

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
  "Copy all mp3s in a given directory to a new directory with sorted names, listing the new filenames."
  [in out]
  (let [files (sort-mp3-list (list-mp3s in))
        outpaths (map-indexed (fn [i v] (str (fs/path out)
                                             fs/file-separator
                                             (pad i)
                                             "."
                                             (fs/extension v)))
                              files)]
    (doall (map-indexed (fn [i v] (fs/copy v (nth outpaths i))) files))))

(defn create-list-txt
  "Create 'list.txt' file in the outpath suitable for ffmpeg concatenate."
  [l outpath]
  (let [listpath (str outpath fs/file-separator "list.txt")
        text (str/join "\n" (map #(str "file '" (fs/expand-home %) "'") l))]
    (spit listpath text)))

(defn ffmpeg-join
  "Conjoin a list of mp3 files (declared in a list.txt file) by calling ffmpeg."
  [list-text-path outdir]
  (let [outfile (str outdir fs/file-separator "out.mp3")]
    (sh "ffmpeg" "-f" "concat" "-safe" "0" "-i" list-text-path outfile)))

(defn conjoin
  "Run the conjoining process."
  [inpath outpath]
  (let [tempdir (fs/create-temp-dir)]
    (println (str "Sorting and copying mp3s found in " inpath "..."))
    (sort-and-copy inpath tempdir)
    (println (str "Creating list.txt file in " tempdir "..."))
    (create-list-txt (sort-mp3-list (list-mp3s tempdir)) tempdir)
    (println (str "List file:\n"
                  (slurp (str tempdir fs/file-separator "list.txt"))))
    (println (str "Joining mp3s in " tempdir "..."))
    (ffmpeg-join (str tempdir fs/file-separator "list.txt") outpath)
    (println (str "Deleting temporary directory " tempdir "..."))
    (fs/delete-tree tempdir)
    (println (str "Renaming " outpath fs/file-separator
                  "out.mp3 to " (fs/file-name inpath) ".mp3"))
    (fs/move (str outpath fs/file-separator "out.mp3")
             (str outpath fs/file-separator (fs/file-name inpath) ".mp3"))))

(def cli-spec {:input  {:ref          "<directory>"
                        :desc         "The input directory (which contains mp3s)."
                        :alias        :i
                        :default      nil}
               :output {:ref          "<directory>"
                        :desc         "The output directory (to save a joined mp3)."
                        :alias        :o
                        :default      nil}})

(defn -main
  "Run the conjoining process."
  [& args]
  (let [arguments (cli/parse-opts (first args) (:spec cli-spec))
        inpath (first (filter some? [(:input arguments) (:i arguments)]))
        outpath (first (filter some? [(:output arguments) (:o arguments)]))]
    (if (and inpath outpath)
      (if (and (fs/exists? (fs/expand-home inpath))
               (has-mp3s? (fs/expand-home inpath))
               (fs/exists? (fs/expand-home outpath)))
        (conjoin (fs/expand-home inpath) (fs/expand-home outpath))
        (println "Input or output directory does not exist or does not contain mp3s."))
      (println (str "Usage: conjoin.clj -i <input dir> -o <output dir>"
                    "\n\n"
                    (cli/format-opts {:spec cli-spec}))))))
    
(-main *command-line-args*)
