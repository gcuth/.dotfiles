#!/usr/bin/env bb
;;
;; A small babashka script which finds audio files in a given directory
;; (or subdirs), splits them into left & right channels, & sends them to
;; local Whisper model for transcription.
;; (Skips any files that have already been transcribed in this way.)

(require '[babashka.fs :as fs]
         '[clojure.string :as s]
         '[babashka.process :as sh]
         '[cheshire.core :as json]
         '[babashka.cli :as cli])

;; Check for required executables ---------------------------------------------
(def ffmpeg-installed? (boolean (fs/which "ffmpeg")))
(def whisper-installed? (boolean (fs/which "whisper")))
(def whisper-mps-installed? (boolean (fs/which "whisper-mps")))


(defn list-audio-files
  "Walk the given directory and return a list of all audio files found."
  ([dir]
   (list-audio-files dir ["mp3" "m4a" "wav" "WAV"]))
  ([dir exts]
   (let [exts (set exts)
         files (map fs/unixify (fs/glob (fs/expand-home dir) "**" {:recursive true}))]
     (filter #(contains? exts (fs/extension %)) files))))


(defn has-transcription?
  "Check if a transcription file already exists for the given audio file."
  [fp]
  (fs/exists? (str (s/replace fp #"\.\w+$" ".txt"))))

(defn has-json?
  "Check if a json file already exists for the given audio file."
  [fp]
  (fs/exists? (str (s/replace fp #"\.\w+$" ".json"))))


(defn is-new?
  "Check if a given file has been modified in the last n days (default 7)."
  [fp & [n]]
  (let [n (or n 7)
        now (System/currentTimeMillis)
        last-modified (-> fp fs/file .lastModified)]
    (<= (- now last-modified) (* n 86400000))))


(defn sort-by-creation-date
  "Sort a list of files by their creation date."
  [files]
  (sort-by #(.lastModified (fs/file %)) files))


(defn sort-by-size
  "Sort a list of files by their size."
  [files]
  (sort-by #(.length (fs/file %)) files))


(defn make-mono
  "Take a path to an audio file and convert it to mono using ffmpeg.
   Save the result in the output directory and return its path."
  [audio-file output-directory]
  (let [filename (fs/strip-ext (fs/file-name audio-file))
        mono-path (str output-directory "/" filename " MONO.wav")]
    (println (str "Converting " audio-file " to mono at " mono-path))
    (sh/sh "ffmpeg" "-i" audio-file "-ac" "1" mono-path)
    (println (str "Saved mono file to " mono-path))
    mono-path))


(defn split-channels
  "Take a path to an audio file and convert it to multiple mono files using
   ffmpeg, saving the result to the output directory and returning a list of
   the paths to the new files."
  [audio-file output-directory]
  (let [filename (fs/strip-ext (fs/file-name audio-file))
        left-path (str output-directory "/" filename " LEFT.wav")
        right-path (str output-directory "/" filename " RIGHT.wav")]
    (println (str "Splitting " audio-file " into left and right channels at "
                  left-path " and " right-path))
    (sh/sh "ffmpeg" "-i" audio-file
           "-filter_complex" "[0:a]channelsplit=channel_layout=stereo[left][right]"
                              "-map" "[left]" left-path
                              "-map" "[right]" right-path)
    (println (str "Saved left channel to " left-path))
    (println (str "Saved right channel to " right-path))
    [left-path right-path]))


(defn enforce-availability
  "Check if the given executables are available and exit if not."
  [& executables]
  (doseq [exe executables]
    (when-not (fs/which exe)
      (prn (str "Error: "
                exe
                " not found on PATH. Please install it and try again."))
      (System/exit 1))))


(defn transcribe
  "Transcribe the given audio file using the Whisper model (mps if available)."
  [audio-file output-directory]
  (let [input-directory (fs/unixify (fs/parent audio-file))
        filename (fs/file-name audio-file)]
    (println (str "Files currently in directory " input-directory " --- " (into [] (map fs/unixify (fs/glob (fs/expand-home input-directory) "**" {:recursive true})))))
    (println (str "Transcribing " audio-file " using whisper-mps ..."))
    (println (str "Input Directory: " input-directory))
    (println (str "Filename: " filename))
    (println (str "Files currently in directory " input-directory " --- " (into [] (map fs/unixify (fs/glob (fs/expand-home input-directory) "**" {:recursive true})))))
    (println ("Beginning transcription of " audio-file))
    (sh/shell {:dir input-directory}
              "whisper-mps" "--file-name" audio-file "--model-name" "large")
    (println (str "Transcription complete for " audio-file))
    (println (str "Files currently in directory " input-directory " --- " (into [] (map fs/unixify (fs/glob (fs/expand-home input-directory) "**" {:recursive true})))))
    (println (str "Moving output.json to " output-directory))
    (fs/move (str input-directory "/output.json")
             (str output-directory "/" (fs/strip-ext filename) ".json"))
    (println (str "Saved json file to " (str output-directory "/" (fs/strip-ext filename) ".json")))
    (println (str "Files currently in directory " input-directory " --- " (into [] (map fs/unixify (fs/glob (fs/expand-home input-directory) "**" {:recursive true})))))
    (str output-directory "/" (fs/strip-ext filename) ".json")))


(defn seconds->timestamp
  [seconds]
  (let [hours (int (/ seconds 3600))
        minutes (int (mod (/ seconds 60) 60))
        seconds (int (mod seconds 60))]
    ;; left-pad the hours, minutes, and seconds with 0s if necessary
    (str hours ":" (format "%02d" hours) ":" (format "%02d" seconds))))


(defn json->txt
  "Convert the json output file of a Whisper transcription to a plain text file."
  [json-file outpath]
  (let [json-data (json/parse-string (slurp json-file) true)]
    ;; for each item in the :segments array, extract the :text and convert the
    ;; :start and :end (floats) to timestamps, outputting the result as a string
    (spit outpath
          (s/join "\n\n"
                  (map #(str (seconds->timestamp (:start %))
                             " --> "
                             (seconds->timestamp (:end %))
                             "\n"
                             (:text %))
                       (:segments json-data))))))


(def cli-spec
  {:input {:help "Directory path containing audio files to transcribe."
           :parse-fn str}
   :mono {:help "Transcribe files as mono (default is stereo)."
          :default false
          :parse-fn boolean}
   :n {:help "The number of files to transcribe (default is 10)."
       :default 10
       :parse-fn #(Integer/parseInt %)}})


(defn -main
  "Process any audio files found in the directory given as a command line arg.
   
   Creates a temporary directory at the start of the process and deletes it at
   the end. This directory is used to store the mono audio files & transcripts.
   The original audio files are not modified."
  []
  (let [input (cli/parse-args *command-line-args* {:spec cli-spec})
        dir (get-in input [:opts :input])
        mono? (get-in input [:opts :mono])
        n (get-in input [:opts :n])
        audio-files (list-audio-files dir)
        transcribable (take n (sort-by-creation-date
                               (filter #(and (not (has-transcription? %))
                                             (not (has-json? %))
                                            ;;  (not (is-new? % 1))
                                             )
                                       audio-files)))]
    (enforce-availability "ffmpeg" "whisper-mps")
    (println "Starting transcription process ...")
    (println (str "Finding audio files in input directory: " dir))
    (println (str "Total audio files found: " (count audio-files)))
    (println (str "Total files to transcribe: " (count transcribable)))
    ;; for each transcribable file ...
    (doseq [f transcribable]
      (fs/with-temp-dir [tmp-dir]
        (let [basename (fs/strip-ext (fs/file-name f))
              outpath (str (fs/strip-ext f) ".json")]
          (println (str "Temp Directory: " tmp-dir))
          (println (str "Target Output Path for Transcription: " outpath))
          (if mono?
            (str "Transcribing file as a mono file: " f)
            (str "Transcribing file as stereo: " f))
          (if mono?
            (try
              (let [mono-path (make-mono f tmp-dir)
                    temp-transcription-path (transcribe mono-path tmp-dir)]
                (fs/move temp-transcription-path outpath)
                (println "Saved json file to " outpath))
              (catch Exception e
                (println (str "Error transcribing " f ": " (.getMessage e)))))
            (try
              (let [[left-path right-path] (split-channels f tmp-dir)
                    temp-transcription-path-left (transcribe left-path tmp-dir)
                    temp-transcription-path-right (transcribe right-path tmp-dir)]
                ;; move transcriptions to the output directory
                (fs/move temp-transcription-path-left
                         (str (fs/strip-ext outpath) "-LEFT.json"))
                (fs/move temp-transcription-path-right
                         (str (fs/strip-ext outpath) "-RIGHT.json"))
                (println "Saved json files to "
                         (str (fs/strip-ext outpath) "-LEFT.json")
                         " and "
                         (str (fs/strip-ext outpath) "-RIGHT.json")))
              (catch Exception e
                (println (str "Error transcribing " f ": " (.getMessage e)))))))))))


  (-main)