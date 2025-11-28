#!/usr/bin/env bb
;; =============================================================================
;; ROLL.BB - Dice Rolling Utility
;; =============================================================================
;; A simple dice roller for tabletop RPGs and quick random number generation.
;;
;; Usage:
;;   roll.bb 1d20        # Roll one 20-sided die
;;   roll.bb 2d6+3       # Roll 2d6 and add 3
;;   roll.bb d10         # Roll one 10-sided die (shorthand)
;;   roll.bb 4d6-1       # Roll 4d6 and subtract 1
;;   roll.bb --help      # Show help
;;
;; =============================================================================

(require '[clojure.string :as str]
         '[babashka.cli :as cli])

(def version "1.0.0")

;; -----------------------------------------------------------------------------
;; Dice Rolling Logic
;; -----------------------------------------------------------------------------

(defn parse-roll
  "Parse a dice roll string into a map.

   Examples:
     '1d10+1' -> {:dice 1 :sides 10 :modifier 1}
     '6d4-3'  -> {:dice 6 :sides 4 :modifier -3}
     'd20'    -> {:dice 1 :sides 20 :modifier 0}"
  [roll-string]
  (let [roll-string (str/trim (str/lower-case roll-string))
        ;; Check if it starts with 'd' (shorthand for 1d)
        has-dice-count? (not (str/starts-with? roll-string "d"))
        ;; Extract numbers
        parts (str/split roll-string #"[d+-]")
        parts (filter #(not (str/blank? %)) parts)
        ;; Parse components
        dice (if has-dice-count?
               (Integer/parseInt (first parts))
               1)
        sides (if has-dice-count?
                (Integer/parseInt (second parts))
                (Integer/parseInt (first parts)))
        ;; Handle modifier
        modifier (cond
                   (str/includes? roll-string "+")
                   (Integer/parseInt (last parts))

                   (str/includes? roll-string "-")
                   (- (Integer/parseInt (last parts)))

                   :else 0)]
    {:dice dice
     :sides sides
     :modifier modifier
     :original roll-string}))

(defn roll-single-die
  "Roll a single die with n sides."
  [sides]
  (inc (rand-int sides)))

(defn roll-dice
  "Roll dice according to the roll map and return detailed results."
  [{:keys [dice sides modifier original]}]
  (let [rolls (repeatedly dice #(roll-single-die sides))
        subtotal (reduce + rolls)
        total (+ subtotal modifier)]
    {:rolls (vec rolls)
     :subtotal subtotal
     :modifier modifier
     :total total
     :expression original}))

(defn format-result
  "Format the roll result for display."
  [{:keys [rolls subtotal modifier total expression]} verbose?]
  (if verbose?
    (let [rolls-str (str/join " + " rolls)
          mod-str (cond
                    (pos? modifier) (str " + " modifier)
                    (neg? modifier) (str " - " (Math/abs modifier))
                    :else "")]
      (str expression " => [" rolls-str "]" mod-str " = " total))
    (str expression " => " total)))

;; -----------------------------------------------------------------------------
;; CLI
;; -----------------------------------------------------------------------------

(def cli-spec
  {:help {:coerce :boolean
          :alias :h
          :desc "Show help message"}
   :version {:coerce :boolean
             :alias :v
             :desc "Show version"}
   :verbose {:coerce :boolean
             :alias :V
             :desc "Show individual die rolls"}})

(defn print-help []
  (println "roll.bb - Dice rolling utility for tabletop RPGs")
  (println)
  (println "Usage: roll.bb [OPTIONS] <dice-expression>")
  (println)
  (println "Dice Expression Format:")
  (println "  NdS      Roll N dice with S sides (e.g., 2d6)")
  (println "  NdS+M    Roll NdS and add modifier M (e.g., 1d20+5)")
  (println "  NdS-M    Roll NdS and subtract modifier M (e.g., 2d6-1)")
  (println "  dS       Shorthand for 1dS (e.g., d20)")
  (println)
  (println "Options:")
  (println "  -h, --help     Show this help message")
  (println "  -v, --version  Show version")
  (println "  -V, --verbose  Show individual die rolls")
  (println)
  (println "Examples:")
  (println "  roll.bb d20           # Roll a d20")
  (println "  roll.bb 2d6+3         # Roll 2d6 and add 3")
  (println "  roll.bb 4d6 --verbose # Roll 4d6, show each die")
  (println "  roll.bb 1d100         # Roll percentile"))

(defn print-version []
  (println (str "roll.bb version " version)))

(defn -main [& args]
  (let [{:keys [opts args]} (cli/parse-args (first args) {:spec cli-spec})]
    (cond
      (:help opts)
      (print-help)

      (:version opts)
      (print-version)

      (empty? args)
      (do
        (println "Error: No dice expression provided.")
        (println "Usage: roll.bb <dice-expression>")
        (println "Try 'roll.bb --help' for more information.")
        (System/exit 1))

      :else
      (try
        (let [roll-str (first args)
              roll-map (parse-roll roll-str)
              result (roll-dice roll-map)]
          (println (format-result result (:verbose opts))))
        (catch Exception e
          (println (str "Error: Invalid dice expression '" (first args) "'"))
          (println "Expected format: NdS, NdS+M, or NdS-M (e.g., 2d6, 1d20+5)")
          (System/exit 1))))))

(-main *command-line-args*)
