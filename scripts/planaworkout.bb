#!/usr/bin/env bb
;;
;; A small script to generate a workout plan in taskpaper format.
;; Designed to be suitable for use with OmniFocus.
;; 
;; Takes an input directory of JSON files, where each is a log of an individual
;; set of a (weight)lifting exercise. Each has the following format (example):
;;
;; {
;;   "exercise": "Back Squat",
;;   "device_name": "My iPhone",
;;   "datetime": "2023-10-27T13:28:14+11:00",
;;   "kg": 35,
;;   "reps": 5,
;;   "percieved_difficulty": "8",
;;   "device_model": "iPhone",
;;   "device_type": "iPhone"
;; }
;;
;; By default, the script will look for files in the target dir, using the
;; history to determine a suitable next set/rep/exercise group. It will then
;; generate a complete workout.


(require '[babashka.fs :as fs]
         '[cheshire.core :as json]
         '[babashka.cli :as cli]
         '[clojure.string :as str]
         '[clojure.instant :as inst])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; BASIC UTILS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn average
  "Calculate the average of a seq of numbers."
  [coll]
  (/ (reduce + coll) (count coll)))


(defn median
  "Calculate the median of a seq of numbers."
  [coll]
  (let [sorted-coll (sort coll)
        n (count sorted-coll)]
    (if (even? n)
      (/ (+ (nth sorted-coll (/ n 2)) (nth sorted-coll (dec (/ n 2)))) 2)
      (nth sorted-coll (/ n 2)))))


(defn mode
  "Calculate the mode of a seq of numbers."
  [coll]
  (let [best-guess (->> coll
                        frequencies
                        (sort-by second)
                        (partition-by second)
                        last
                        (map first))]
    (if (= 1 (count best-guess))
      (first best-guess)
      (median best-guess))))


(defn ewma
  "Calculate the exponentially weighted moving average of a vector of numbers.
    EWMA(t) = a * x(t) + (1-a) * EWMA(t-1)
  (ie, the EWMA value at time t)
  Takes an optional smoothing parameter (a), which defaults to 0.5"
  ([coll] (ewma coll 0.5))
  ([coll a]
   (rest (reduce (fn [ewma-values x]
                   (let [prev-ewma (last ewma-values)
                         ewma (+ (* a x)
                                 (* (- 1 a) prev-ewma))]
                     (conj ewma-values ewma)))
                 [0.0] coll))))


(defn list-json-files
  "List all files in a given path that end in .json"
  [path]
  (map #(fs/unixify %) (fs/glob path "*.json")))


(defn read-logs
  "Read a list of JSON file paths into a list of maps."
  [files]
  (->> files
       (map #(json/parse-string (slurp %) true))
       (map #(assoc % :timestamp
                    (.getTime (inst/read-instant-date (:datetime %)))))
       (map #(assoc % :date
                    (first (str/split (:datetime %) #"T"))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;; EXERCISE STANDARDS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def EXERCISES
  ["Overhead Press"
   "Bench Press"
   "Barbell Row"
   "Back Squat"
   "Deadlift"
   "Barbell Bicep Curl"])


(defn measure-orm-level
  "Take an exercise name & 1RM, along with optionally 'gender', & 'bodyweight'.
   Use rough 'strength levels' to return a continuous score between 0 and 5,
   interpolating between defined thresholds."
  [& {:keys [exercise orm gender bodyweight]}]
  (let [bodyweight (or bodyweight
                       (cond (= gender "male") 85
                             (= gender "female") 60
                             :else 70))
        fractional-orm (/ orm bodyweight)
        all-thresholds (cond
                         (= gender "male")
                         {"Overhead Press" [0.35 0.55 0.8 1.1 1.4]
                          "Bench Press" [0.5 0.75 1.25 1.75 2]
                          "Barbell Row" [0.5 0.75 1 1.5 1.75]
                          "Back Squat" [0.75 1.25 1.5 2.25 2.75]
                          "Deadlift" [1 1.5 2 2.5 3]
                          "Barbell Bicep Curl" [0.2 0.4 0.6 0.85 1.15]}
                         (= gender "female")
                         {"Overhead Press" [0.2 0.35 0.5 0.75 1]
                          "Bench Press" [0.25 0.5 0.75 1 1.5]
                          "Barbell Row" [0.25 0.4 0.65 0.9 1.2]
                          "Back Squat" [0.5 0.75 1.25 1.5 2]
                          "Deadlift" [0.5 1 1.25 1.75 2.5]
                          "Barbell Bicep Curl" [0.1 0.2 0.4 0.6 0.85]}
                         :else ;; halfway between the above
                         {"Overhead Press" [0.275 0.45 0.65 0.925 1.2]
                          "Bench Press" [0.375 0.625 1 1.375 1.75]
                          "Barbell Row" [0.375 0.575 0.825 1.2 1.475]
                          "Back Squat" [0.625 1 1.375 1.875 2.375]
                          "Deadlift" [0.75 1.25 1.625 2.125 2.75]
                          "Barbell Bicep Curl" [0.15 0.3 0.5 0.725 1]})
        thresholds (all-thresholds exercise)
        interpolate-score (fn [fractional-orm thresholds]
                            ;; given the fractional orm and relevant thresholds
                            ;; return a score between 0 and 5. Interpolate
                            ;; between thresholds. (ie, if the fractional orm
                            ;; is 0.3, and the thresholds are [0.2 0.35 0.5 0.75 1],
                            ;; then the score is )
                            )]
    thresholds))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;; EXERCISE SUGGESTIONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn days-since-exercise
  "Given a list of logs & an exercise (passed as named args),
   return number of days since last done."
  [& {:keys [logs exercise]}]
  (let [now (quot (System/currentTimeMillis) 1000)]
    (->> logs
         (filter #(= (:exercise %) exercise)) ;; get logs for the exercise
         (map :timestamp) ;; get timestamps for each of the sets
         (map #(quot % 1000)) ;; strip milliseconds from each timestamp
         (map #(- now %)) ;; subtract from current timestamp to get difference
         (map #(quot % 86400)) ;; convert to days (86400 seconds in a day)
         (sort) ;; sort the list of days
         (first)))) ;; return the first (most recent) day


(defn recent-weight
  "Given a list of logs & an exercise (passed as named args), return an integer
   value of the most recent weight used for that exercise (calc using ewma)."
  [& {:keys [logs exercise]}]
  (->> logs
       (filter #(= (:exercise %) exercise)) ;; get logs for the exercise
       (map :kg) ;; get weights for each of the sets
       (ewma) ;; calculate the ewma of the weights
       (last) ;; return the last (most recent) ewma value
       (int))) ;; round to the nearest integer


(defn latest-weight
  "Given a list of logs, an exercise, and a target rep count (passed as named
   args) return the most recent weight used for that exercise at the rep count.
   If none exists, return nil."
  [& {:keys [logs exercise target-reps]}]
  (->> logs
       (filter #(= (:exercise %) exercise)) ;; get logs for the exercise
       (filter #(= (:reps %) target-reps)) ;; get logs for the target rep count
       (sort-by :timestamp)
       (last)
       :kg))


(defn recent-reps
  "Given a list of logs & an exercise (passed as named args), return an integer
   value of the most recent reps used for that exercise (calc using ewma)."
  [& {:keys [logs exercise]}]
  (->> logs
       (filter #(= (:exercise %) exercise)) ;; get logs for the exercise
       (map :reps) ;; get reps for each of the sets
       (ewma) ;; calculate the ewma of the reps
       (last) ;; return the last (most recent) ewma value
       (int))) ;; round to the nearest integer


(defn latest-reps
  "Given a list of logs, an exercise, and a target weight (passed as named
   args) return the most recent reps used for that exercise at the weight.
   If none exists, return nil."
  [& {:keys [logs exercise target-kg]}]
  (->> logs
       (filter #(= (:exercise %) exercise)) ;; get logs for the exercise
       (filter #(= (:kg %) target-kg)) ;; get logs for the target weight
       (sort-by :timestamp)
       (last)
       :reps))


(defn estimate-orm
  "Given a list of logs & an exercise (passed as named args), return an integer
   value of the estimated one rep max for that exercise.
   To do this, we use the 'Epley' formula:
   w * (1 + r/30)
   where w is the weight used, and r is the number of reps."
  [& {:keys [logs exercise]}]
  (let [recent-logs (->> logs
                         (filter #(= (:exercise %) exercise)) ;; exercise logs
                         (sort-by :timestamp)) ;; sort by timestamp
        recent-weights (map :kg recent-logs) ;; get weights for each set
        recent-reps (map :reps recent-logs) ;; get reps for each of the set
        recent-orms (map
                     #(int (* % (+ 1 (/ %2 30))))
                     recent-weights
                     recent-reps)]
    (->> recent-orms
         (ewma) ;; calculate the ewma of the orms
         (last) ;; return the last (most recent) ewma value
         (int)))) ;; round to the nearest integer


(defn sets-per-day
  "Given a list of logs & an exercise, return the average number of sets per
   day for that exercise. To do this, for each log of a set, get the number of
   days since it was first performed. Then, count the total number of sets ever
   performed & divide by the number of days since the first set was performed."
  [& {:keys [logs exercise]}]
  (let [now (quot (System/currentTimeMillis) 1000)
        total-sets (count (filter #(= (:exercise %) exercise) logs))
        days-since-first (->> logs
                              (filter #(= (:exercise %) exercise)) ;; get logs
                              (map :timestamp) ;; get timestamps for each set
                              (map #(quot % 1000)) ;; strip milliseconds
                              (map #(- now %)) ;; subtract current timestamp
                              (map #(quot % 86400)) ;; convert to days
                              (sort)
                              (reverse)
                              (first))]
    (/ total-sets days-since-first)))


(defn suggest-weight
  "Given a list of logs, a target rep count, & an exercise (as named args),
   return an integer value for the suggested weight."
  [& {:keys [logs exercise target-reps bodyweight gender age]}]
  (let [est-orm (estimate-orm :logs logs :exercise exercise) ;; estimated 1RM
        days-since (days-since-exercise :logs logs :exercise exercise)
        discount-factor (if (< days-since 7) 1
                            ;; discount by 4.5% for each day since last done
                            (- 1 (* (/ 45 1000) (- days-since 7))))
        discounted-orm (* est-orm discount-factor) ;; discount days since done
        discounted-weight (* discounted-orm (- 0.75 (* 0.05 (- target-reps 4))))
        latest (* (latest-weight
                   :logs logs
                   :exercise exercise
                   :target-reps target-reps)
                  discount-factor)
        best-guess (int (median [discounted-weight latest]))
        form-safe (if (or (< (sets-per-day :logs logs :exercise exercise)
                             (cond (= exercise "Back Squat") (/ 12 7)
                                   (= exercise "Deadlift") (/ 3 7)
                                   :else (/ 8 7)))
                          (> (sets-per-day :logs logs :exercise exercise) 3))
                    (int (* 0.8 best-guess)) ;; discount by 20%
                    best-guess)
        final-weight (cond (< form-safe 20) 20
                           (zero? (mod form-safe 5)) form-safe
                           (> form-safe bodyweight) form-safe
                           :else (+ (rand-nth [0 5])
                                    (* 5 (inc (quot form-safe 5)))))
        orm-level (measure-orm-level :exercise exercise
                                     :orm discounted-orm
                                     :gender gender
                                     :bodyweight bodyweight
                                     :age age)]
    final-weight))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;; WORKOUT GENERATION UTILS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn days-since-last-lifting-workout
  "Get the minimum number of days since any set was recorded for any exercises
   in the given list."
  ([logs]
   (days-since-last-lifting-workout logs EXERCISES))
  ([logs exercises]
   (apply min (map #(days-since-exercise :logs logs :exercise %) exercises))))


(defn count-lifting-days-in-n-days
  "Get the number of days in the last n days that a lifting workout was done."
  [logs n]
  (let [now (quot (System/currentTimeMillis) 1000)]
    (->> logs
         (map :timestamp) ;; get timestamps for each set
         (map #(quot % 1000)) ;; strip milliseconds
         (map #(- now %)) ;; subtract from current timestamp
         (map #(quot % 86400)) ;; convert to days
         (distinct) ;; remove duplicates
         (sort) ;; sort the list of days
         (take-while #(<= % (- n 1))) ;; distinct days in seq less than n ago
         (count)))) ;; count the number of days in the seq


(defn build-task
  "Given a map, build a taskpaper task."
  [task]
  (str "- " (:text task)
       (if (:estimate task) (str " @estimate(" (:estimate task) "m)") "")
       (if (:tags task) (str " @tags(" (str/join ", " (:tags task)) ")") "")
       "\n"))


(defn generate-ladder
  "Given a target weight, generate a 'ladder' of n increasing weights.
   These can then be assigned to sets. The target weight can be any integer,
   but the minimum weight is 20kg, and all intervening weights should be
   multiples of 5kg, 10kg, or 20kg where possible. Prioritise 'even' movement
   towards the target weight. There is *always* a warmup set of 20kg if n > 1.

   eg, if the target weight is 54kg, and n is 5, then the ladder would be
         [20 30 40 50 54]
       if the target weight is 30, and n is 5, then the ladder would be
         [20 20 25 25 30]
   
   To achieve this, we start at the target weight and work backwards, then
   reverse the resulting sequence."
  [target-weight n]
  (let [target-weight (max 20 target-weight)
        step-size (max 5 (abs (quot (- 20 target-weight) (max 1 (dec n)))))
        steps (map (fn [x] (if (< x 20) 20 x))
                   (rest (map #(int (* 5 (quot (+ 2 %) 5)))
                              (iterate #(+ % (* -1 step-size))
                                       (int target-weight)))))]
    (conj (rest (reverse (conj (take (dec n) steps) target-weight))) 20)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;; ACTUAL WORKOUT GENERATION ;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn generate-quick-workout
  "Generate a quick workout for when you're short on time.
   Assumes you have all the equipment at home (and roughly set up already)."
  [logs bodyweight]
  (let [bench (latest-weight :logs logs
                             :exercise "Bench Press"
                             :target-reps 5)
        ohead (latest-weight :logs logs
                             :exercise "Overhead Press"
                             :target-reps 5)
        dlift (latest-weight :logs logs
                             :exercise "Deadlift"
                             :target-reps 5)
        squat (latest-weight :logs logs
                             :exercise "Back Squat"
                             :target-reps 5)]
    (str "- Complete a quick workout @parallel(false) @autodone(true) @estimate(55m) @due(5pm) @defer(4am)\n"
         "\t- Get changed into running gear @estimate(5m) @tags(Low, Home)\n"
         "\t- Put on running shoes @estimate(1m) @tags(Low, Home)\n"
         "\t- Go for a 4km run @estimate(25m) @tags(High, Home, Fitness)\n"
         "\t- Check that bench press bar is loaded to a total of " bench "kgs @estimate(1m) @tags(Low, Home, Fitness)\n"
         "\t- Check that overhead press bar is loaded to a total of " ohead "kgs @estimate(1m) @tags(Low, Home, Fitness)\n"
         "\t- Check that deadlift bar is loaded to a total of " dlift "kgs @estimate(1m) @tags(Low, Home, Fitness)\n"
         "\t- Bench Press " bench "kg for 5 reps @estimate(3m) @tags(High, Home, Fitness)\n"
         "\t- Overhead Press " ohead "kg for 5 reps @estimate(3m) @tags(High, Home, Fitness)\n"
         "\t- Deadlift " dlift "kg for 5 reps @estimate(3m) @tags(High, Home, Fitness)\n"
         "\t- Check that squat bar is loaded to a total of " squat "kgs @estimate(1m) @tags(Low, Home, Fitness)\n"
         "\t- Back Squat " squat "kg for 5 reps @estimate(3m) @tags(High, Home, Fitness)\n"
         "\t- Pullups for 5 reps @estimate(3m) @tags(High, Home, Fitness)\n"
         "\t- Have a cold shower @estimate(5m) @tags(Low, Home, Mindfulness)\n")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CLI ARGUMENTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def cli-spec ;; CLI argument spec.
  {:input {:default "~/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/Logs/Fitness/Lifting/"
           :help "The input directory to process."
           :parse-fn str}
   :bodyweight {:default 85
                :help "Your bodyweight in kg."
                :parse-fn int}
   :gender {:default nil
            :help "Your gender (opts: male or female)"
            :parse-fn str}
   :quick {:default false
           :help "Generate a quick workout instead of a full workout."
           :parse-fn :boolean}
   :n      {:default 1
            :help "Number of workouts to generate."
            :parse-fn int}
   :help {:coerce :boolean}})


(def cli-aliases ;; CLI argument aliases for convenience.
  {:i :input})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; -MAIN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn -main
  "Process command line args, generate a workout, and output."
  []
  (let [input (cli/parse-args *command-line-args*
                              {:spec cli-spec
                               :aliases cli-aliases})
        args (:args input)
        opts (:opts input)
        logs (-> opts
                 :input
                 (fs/expand-home)
                 (fs/unixify)
                 list-json-files
                 read-logs)
        bodyweight (:bodyweight opts)]

    (println (generate-quick-workout logs bodyweight))))


(-main)