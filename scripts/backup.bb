#!/usr/bin/env bb
;; =============================================================================
;; BACKUP.BB - Encrypted Directory Backup
;; =============================================================================
;; Creates GPG-encrypted tar archives of directories.
;;
;; Usage:
;;   backup.bb --input <dir> --output <path>   Create encrypted backup
;;   backup.bb --help                          Show this help message
;;
;; Requirements:
;;   - tar (standard on macOS/Linux)
;;   - gpg (brew install gpg)
;;
;; Output:
;;   Encrypted tarball: YYYY-MM-DD-HH-MM-SS-dirname.tar.gz.gpg
;; =============================================================================



(require '[babashka.fs :as fs]
         '[babashka.cli :as cli]
         '[clojure.string :as str]
         '[babashka.process :refer [shell process pipeline pb]]
         '[clojure.java.io :as io])

(defn- has-command?
  "Check if a command is available on the system."
  [cmd]
  (boolean (fs/which cmd)))

(defn- is-directory?
  "Check if a path is a valid, accessible, existing directory."
  [path]
  (and (fs/exists? path)
       (fs/directory? path)
       (fs/readable? path)))

(defn- dtstamp
  "Get a timestamp string of the form YYYY-MM-DD-HH-MM-SS."
  []
  (-> (java.time.LocalDateTime/now)
      str
      (str/split #"\.")
      first
      (str/replace #"[\s:T]" "-")))

(defn- clean-target
  "Given a raw target path, create a clean/valid path for the backup."
  ([target] (clean-target target "BACKUP"))
  ([target name]
   (let [expanded (fs/expand-home target)
         unixified (fs/unixify expanded)]
     (if (fs/directory? expanded)
       (fs/path unixified
                (format "%s-%s.tar.gz.gpg"
                        (dtstamp)
                        (or name "BACKUP")))
       unixified))))

(defn- create-parent-dirs!
  "Ensure parent directories exist for a given path."
  [path]
  (let [parent (fs/parent path)]
    (when (and parent (not (fs/exists? parent)))
      (fs/create-dirs parent))))

(defn backup!
  "Given the input & output paths, run the backup process.
   Returns {:success boolean :message string}."
  [input output]
  (try
    (let [source (-> input fs/expand-home fs/unixify fs/file)
          name (-> source fs/file-name fs/strip-ext)
          target (clean-target output name)
          parent-dir (fs/parent source)
          dir-name (fs/file-name source)]
      (println (format "Backing up %s to %s" source target))
      ;; Create the pipeline of processes
      (let [processes (pipeline
                       (pb {:out :pipe
                            :dir parent-dir}
                           "tar" "-zvcf" "-" dir-name)
                       (pb {:in :pipe} "gpg" "-c" "--no-symkey-cache" "--output" (str target)))
            ;; Wait for all processes to complete and get exit codes
            exit-codes (map #(.waitFor (:proc %)) processes)]
        (if (every? zero? exit-codes)
          {:success true
           :message (str (format "Successfully backed up to %s" target)
                         (format "\nTo decrypt, run: gpg -d %s | tar -xvzf -" target))}
          {:success false
           :message (format "Backup failed! Exit codes: %s"
                            (pr-str exit-codes))})))
    (catch Exception e
      {:success false
       :message (format "Error during backup: %s" (.getMessage e))})))

(def cli-spec
  {:input {:desc "Source directory to back up (required)"
           :alias :i
           :require true
           :coerce :string
           :validate {:pred is-directory?
                      :msg "Must be a readable directory"}}
   :output {:desc "Destination path for the backup (required)"
            :alias :o
            :require true
            :coerce :string}
   :help {:desc "Show help message"
          :alias :h
          :coerce :boolean}})

(defn print-help []
  (println "backup.bb - Encrypted Directory Backup")
  (println)
  (println "Creates GPG-encrypted tar archives of directories.")
  (println)
  (println "Usage: backup.bb --input <dir> --output <path>")
  (println)
  (println "Options:")
  (println "  -i, --input <dir>    Source directory to back up (required)")
  (println "  -o, --output <path>  Destination path or directory (required)")
  (println "  -h, --help           Show this help message")
  (println)
  (println "Examples:")
  (println "  backup.bb --input ~/Documents --output ~/Backups/")
  (println "  backup.bb -i ~/Photos -o /Volumes/External/")
  (println)
  (println "Output:")
  (println "  Creates: YYYY-MM-DD-HH-MM-SS-dirname.tar.gz.gpg")
  (println "  You will be prompted for a passphrase by GPG.")
  (println)
  (println "To decrypt:")
  (println "  gpg -d backup.tar.gz.gpg | tar -xvzf -"))

(defn validate-environment! []
  (when-not (has-command? "tar")
    (throw (ex-info "tar command not found" {:type :missing-dependency})))
  (when-not (has-command? "gpg")
    (throw (ex-info "gpg command not found" {:type :missing-dependency}))))

(defn -main [& args]
  (try
    (let [{:keys [help input output] :as opts} (cli/parse-opts args {:spec cli-spec})]
      (cond
        help
        (print-help)

        (empty? args)
        (do
          (println "Usage: backup.bb --input <source-dir> --output <target-path>")
          (println "Try 'backup.bb --help' for more information."))

        :else
        (do
          (validate-environment!)
          (let [{:keys [success message]} (backup! input output)]
            (println message)
            (System/exit (if success 0 1))))))

    (catch clojure.lang.ExceptionInfo e
      (case (:type (ex-data e))
        :missing-dependency (println (.getMessage e))
        (println "Error:" (.getMessage e)))
      (System/exit 1))

    (catch Exception e
      (println "Unexpected error:" (.getMessage e))
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
