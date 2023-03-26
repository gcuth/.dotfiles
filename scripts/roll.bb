#!/usr/bin/env bb

(:require '[clojure.string :as str])

(defn translate-roll-command
  "Take a string for a dice roll command and return a map containing :dice, :sides, and :modifier
   Examples:
      '1d10+1' -> {:dice 1 :sides 10 :modifier 1}
      '6d4-3' -> {:dice 6 :sides 4 :modifier -3}"
  [roll-command]
  (let [parts (str/split roll-command #"\D+")
        dice (Integer/parseInt (first parts))
        sides (Integer/parseInt (second parts))
        modifier (cond (str/includes? roll-command "-") 
                         (- (Integer/parseInt (last parts)))
                       (str/includes? roll-command "+")
                         (Integer/parseInt (last parts))
                       :else 0)]
    {:dice dice :sides sides :modifier modifier}))

(defn calculate-roll
  "Take a roll (as a map), rand roll, and return the total."
  [roll-map]
  (let [dice (:dice roll-map)
        sides (:sides roll-map)
        modifier (:modifier roll-map)]
    (+ modifier (reduce + (map (fn [_] (inc (rand-int sides))) (range dice))))))



(defn -main [& args]
  (let [input (first args)]
    (if (= 1 (count input))
      (println (calculate-roll (translate-roll-command (first (first args)))))
      (do
        (println "Usage: roll.bb <roll>")
        (println "   eg: roll.bb 1d6+1")))))

(-main *command-line-args*)
