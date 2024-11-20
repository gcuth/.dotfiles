#!/usr/bin/env bb
;;
;; A script for taking a path to a directory and backing it up to an encrypted
;; tarball in a specified target location.

;; Usage: backup.bb --input <source-directory> --output <destination-file-path>



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
           :require true
           :coerce :string
           :validate {:pred is-directory?
                      :msg "Must be a readable directory"}}
   :output {:desc "Destination path for the backup (required)"
            :require true
            :coerce :string}})

(defn validate-environment! []
  (when-not (has-command? "tar")
    (throw (ex-info "tar command not found" {:type :missing-dependency})))
  (when-not (has-command? "gpg")
    (throw (ex-info "gpg command not found" {:type :missing-dependency}))))

(defn -main [& args]
  (try
    (validate-environment!)

    (if (empty? args)
      (println "Usage: backup.bb --input <source-dir> --output <target-path>")
      (let [{:keys [input output] :as opts} (cli/parse-opts args {:spec cli-spec})]
        (let [{:keys [success message]} (backup! input output)]
          (println message)
          (System/exit (if success 0 1)))))

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
