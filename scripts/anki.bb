#!/usr/bin/env bb
;;
;; A small script for saving (possible) anki cards to json files for later
;; verification, processing, and import into Anki.
;;
;; Accepts arguments ('question', 'answer', 'source'), but also allows for
;; interactive input if those arguments are not provided.
;;
;; Output directory defaults to a folder in the iCloud Drive
;; (at '/Shortcuts/Anki/Cards/Drafts/')
;; but also accepts an optional argument ('output') to specify a different
;; output directory.


 (require '[babashka.fs :as fs]
          '[cheshire.core :as json]
          '[babashka.cli :as cli]
          '[clojure.string :as str])

(def DEFAULT-OUTPATH "~/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/Anki/Cards/Drafts/")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; BASIC UTILS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-now
  "Return the current zoned datetime as a string."
  []
  (-> (str (java.time.ZonedDateTime/now))
      (str/replace "T" " ")
      (str/replace #"\[.*\]" "")
      (str/replace #"\..*(?=-)" " ")
      (str/replace #"\..*(?=\+)" " ")
      (str/replace #"(?<= \+\d{2}):" "")
      (str/replace #"(?<= -\d{2}):" "")))


(defn create-card
  "Create a json string card from a question and answer."
  [card]
  {:pre [(map? card) (:question card) (:answer card)
         (string? (:question card)) (string? (:answer card))]}
  (let [timestamp (get-now)
        clean-card (-> card
                       (assoc :created timestamp)
                       (assoc :draft true)
                       (assoc :checked false))]
    (json/generate-string clean-card)))


(defn generate-filename
  "Generate a filename for a card."
  []
  (let [timestamp (get-now)
        cleantime (str/join "-" (-> timestamp
                                    (str/replace #"\:" "-")
                                    (str/split #" ")
                                    (butlast)))
        randomid (str (format "%08d" (rand-int 1000000)))
        filename (str cleantime "-" randomid ".json")]
    filename))


(defn save-card
  "Save a card to a json file."
  ([card]
   {:pre [(or (map? card) (string? card))]}
   (save-card card DEFAULT-OUTPATH))
  ([card output-dir]
   {:pre [(or (map? card) (string? card)) (string? output-dir)]}
   (save-card card output-dir (generate-filename)))
  ([card output-dir filename]
   {:pre [(or (map? card) (string? card)) (string? output-dir) (string? filename)]}
   (let [outpath (fs/unixify (fs/expand-home output-dir))
         filepath (str outpath "/" filename)
         card (if (map? card) (create-card card) card)]
     (spit filepath card))))


(defn get-input
  "Get input from the user."
  [prompt]
  (print prompt)
  (flush)
  (read-line))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CLI ARGUMENTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def cli-spec ;; CLI argument spec.
  {:question {:default nil
              :help "The question to save as an anki card."
              :parse-fn str}
   :answer {:default nil
            :help "The answer to save as an anki card."
            :parse-fn str}
   :tags {:default ""
          :help "The tags to save for the anki card."
          :parse-fn str}
   :source {:default ""
            :help "The source to save for the anki card."
            :parse-fn str}
   :output {:default "~/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/Anki/Cards/Drafts/"
            :help "The output directory to save draft anki card json files to."
            :parse-fn str}
   :help {:coerce :boolean}})


(def cli-aliases ;; CLI argument aliases for convenience.
  {:o :output
   :h :help})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; -MAIN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn -main
  "Process command line args and output."
  []
  (let [input (cli/parse-args *command-line-args*
                              {:spec cli-spec
                               :aliases cli-aliases})
        args (:args input)
        opts (:opts input)
        question (or (:question opts) (get-input "Question: "))
        answer (or (:answer opts) (get-input "Answer: "))
        card (create-card {:question question
                           :answer answer
                           :source (:source opts)
                           :tags (:tags opts)}) 
        output (or (:output opts) DEFAULT-OUTPATH)
        filename (generate-filename)]
    (cond (:help opts) (println "Help text here TK.")
          (not (fs/exists? (fs/unixify (fs/expand-home (:output opts)))))
          (println "Output directory does not exist! Check & try again.")
          :else
          (save-card card output filename))))


(-main)