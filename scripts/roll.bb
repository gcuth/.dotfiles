#!/usr/bin/env bb

(:require '[clojure.string :as str])

(defn cmd->roll
  "Take a string for a dice roll command and return a map containing:
   :dice, :sides, and :modifier
   
   Examples:
      '1d10+1' -> {:dice 1 :sides 10 :modifier 1}
      '6d4-3' -> {:dice 6 :sides 4 :modifier -3}"
  [roll-command]
  (let [parts (str/split roll-command #"\D+")
        has-modifier? (or (str/includes? roll-command "-")
                          (str/includes? roll-command "+"))
        has-dice-n? (not (str/starts-with? (str/trim roll-command) "d"))
        dice (if has-dice-n?
               (Integer/parseInt (first parts))
               1)
        sides (if has-dice-n?
                (Integer/parseInt (second parts))
                (Integer/parseInt (first parts)))
        modifier (cond (str/includes? roll-command "-")
                       (- (Integer/parseInt (last parts)))
                       (str/includes? roll-command "+")
                       (Integer/parseInt (last parts))
                       :else 0)]
    {:dice dice :sides sides :modifier modifier}))


(defn roll-dice
  "Take a roll (as a map), randomly roll, and return the total."
  [roll-map]
  (let [dice (:dice roll-map)
        sides (:sides roll-map)
        modifier (:modifier roll-map)]
    (+ modifier (reduce + (map (fn [_] (inc (rand-int sides))) (range dice))))))



(defn -main [& args]
  (let [input (first args)]
    (if (= 1 (count input))
      (println (str (first (first args))
                    "=>"
                    (roll-dice (cmd->roll (first (first args))))))
      (do
        (println "Usage: roll.bb <roll>")
        (println "   eg: roll.bb 1d6+1")))))

(-main *command-line-args*)
