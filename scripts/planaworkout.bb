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


(defn which-workout?
  "Given a list of logs, decides which workout to do next. Returns a keyword.
   Either: :a, :b, or :cardio."
  ([logs] (which-workout? logs EXERCISES))
  ([logs exercises]
   (let [days-since-last-lift (days-since-last-lifting-workout logs exercises)
         days-since-a (days-since-last-lifting-workout logs ["Overhead Press"])
         days-since-b (days-since-last-lifting-workout logs ["Bench Press"])]
     (cond (>= (count-lifting-days-in-n-days logs 7) 4) :cardio
           (>= (count-lifting-days-in-n-days logs 14) 8) :cardio
           (>= (count-lifting-days-in-n-days logs 21) 12) :cardio
           (>= (count-lifting-days-in-n-days logs 28) 16) :cardio
           (> days-since-a days-since-b) :a
           :else :b))))

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


(defn generate-workout-a
  "Generate a default 'Workout A' taskpaper based on logs."
  ([logs] (generate-workout-a logs 85))
  ([logs bodyweight]
   (let [squat-kg (suggest-weight :logs logs
                                  :exercise "Back Squat"
                                  :target-reps 5
                                  :bodyweight bodyweight)
         squat-weights (generate-ladder 35 6)
         press-kg (suggest-weight :logs logs
                                  :exercise "Overhead Press"
                                  :target-reps 5
                                  :bodyweight bodyweight)
         press-weights (generate-ladder press-kg 6)
         n-pullups (min (max (- (int (/ press-kg 10)) 1) 2) 5)
         deadlift-kg (suggest-weight :logs logs
                                     :exercise "Deadlift"
                                     :target-reps 5
                                     :bodyweight bodyweight)
         start-tasks [{:text "Start workout tracking for Weightlifting"
                       :estimate 1
                       :tags ["Low" "Home"]}]
         end-tasks [{:text "Stop workout tracking for Weightlifting"
                     :estimate 1
                     :tags ["Low" "Home"]}
                    {:text "Have a cold shower"
                     :estimate 5
                     :tags ["Low" "Home" "Mindfulness"]}]
         exercises (remove nil?
                           (concat
                            (flatten (for [i (range 0 6)]
                                       (let [squat (nth squat-weights i)
                                             press (nth press-weights i)]
                                         [(when (and (> i 0) (pos? (- squat (nth squat-weights (dec i)))))
                                            (let [each-side (/ (- squat (nth squat-weights (dec i))) 2)
                                                  to-add (if (integer? each-side)
                                                           (int each-side)
                                                           (float each-side))]
                                              {:text (str "Add " to-add "kg to each side of the squat bar")
                                               :estimate 5
                                               :tags ["Medium" "Home" "Fitness"]}))
                                          {:text (str "Check that squat bar is loaded to a total of " squat "kgs")
                                           :estimate 1
                                           :tags ["Low" "Home" "Fitness"]}
                                          {:text (str "Back Squat " squat "kg for 5 reps")
                                           :estimate 3
                                           :tags ["High" "Home" "Fitness"]}
                                          {:text (str "Do " n-pullups " pullups")
                                           :estimate 1
                                           :tags ["High" "Home" "Fitness"]}
                                          (when (and (> i 0) (pos? (- press (nth press-weights (dec i)))))
                                            (let [each-side (/ (- press (nth press-weights (dec i))) 2)
                                                  to-add (if (integer? each-side)
                                                           (int each-side)
                                                           (float each-side))]
                                              {:text (str "Add " to-add "kg to each side of the overhead press bar")
                                               :estimate 5
                                               :tags ["Medium" "Home" "Fitness"]}))
                                          {:text (str "Check that overhead press bar is loaded to a total of " press "kgs")
                                           :estimate 1
                                           :tags ["Low" "Home" "Fitness"]}
                                          {:text (str "Overhead Press " press "kg for 5 reps")
                                           :estimate 3
                                           :tags ["High" "Home" "Fitness"]}])))
                            [{:text (str "Check that deadlift bar is loaded to a total of " deadlift-kg "kgs")
                              :estimate 1
                              :tags ["Low" "Home" "Fitness"]}
                             {:text (str "Deadlift " deadlift-kg "kg for 5 reps")
                              :estimate 3
                              :tags ["High" "Home" "Fitness"]}
                             {:text (str "Remove all weights from overhead press bar")
                              :estimate 1
                              :tags ["Low" "Home" "Fitness"]}
                             {:text (str "Remove all weights from squat bar")
                              :estimate 1
                              :tags ["Low" "Home" "Fitness"]}]))
         tasks (concat start-tasks exercises end-tasks)
         total-time (reduce + (map :estimate tasks))]
     (str "- Complete 'Workout A' @parallel(false) @autodone(true) @estimate(" (+ 15 total-time) "m) @due(5pm) @defer(4am)\n"
          (apply str (map #(str "\t" %) (map build-task tasks)))))))


(defn generate-workout-b
  "Generate a default 'Workout B' taskpaper based on logs."
  ([logs] (generate-workout-b logs 85))
  ([logs bodyweight]
   (let [squat-kg (suggest-weight :logs logs
                                  :exercise "Back Squat"
                                  :target-reps 5
                                  :bodyweight bodyweight)
         squat-weights (generate-ladder 35 6)
         press-kg (suggest-weight :logs logs
                                  :exercise "Bench Press"
                                  :target-reps 5
                                  :bodyweight bodyweight)
         press-weights (generate-ladder press-kg 6)
         n-pullups (min (max (- (int (/ press-kg 10)) 1) 2) 5)
         curl-kg (suggest-weight :logs logs
                                 :exercise "Barbell Bicep Curl"
                                 :target-reps 5
                                 :bodyweight bodyweight)
         start-tasks [{:text "Start workout tracking for Weightlifting"
                       :estimate 1
                       :tags ["Low" "Home"]}]
         end-tasks [{:text "Stop workout tracking for Weightlifting"
                     :estimate 1
                     :tags ["Low" "Home"]}
                    {:text "Have a cold shower"
                     :estimate 5
                     :tags ["Low" "Home" "Mindfulness"]}]
         exercises (remove nil?
                           (concat
                            (flatten (for [i (range 0 6)]
                                       (let [squat (nth squat-weights i)
                                             press (nth press-weights i)]
                                         [(when (and (> i 0) (pos? (- squat (nth squat-weights (dec i)))))
                                            (let [each-side (/ (- squat (nth squat-weights (dec i))) 2)
                                                  to-add (if (integer? each-side)
                                                           (int each-side)
                                                           (float each-side))]
                                              {:text (str "Add " to-add "kg to each side of the squat bar")
                                               :estimate 5
                                               :tags ["Medium" "Home" "Fitness"]}))
                                          {:text (str "Check that squat bar is loaded to a total of " squat "kgs")
                                           :estimate 1
                                           :tags ["Low" "Home" "Fitness"]}
                                          {:text (str "Back Squat " squat "kg for 5 reps")
                                           :estimate 3
                                           :tags ["High" "Home" "Fitness"]}
                                          {:text (str "Do " n-pullups " pullups")
                                           :estimate 1
                                           :tags ["High" "Home" "Fitness"]}])))
                            [{:text (str "Remove all weights from the squat bar")
                              :estimate 1
                              :tags ["Low" "Home" "Fitness"]}
                             {:text (str "Shift squat barbell to bench press position")
                              :estimate 1
                              :tags ["Low" "Home" "Fitness"]}]
                            (flatten (for [i (range 0 6)]
                                       (let [press (nth press-weights i)]
                                         [(when (and (> i 0) (pos? (- press (nth press-weights (dec i)))))
                                            (let [each-side (/ (- press (nth press-weights (dec i))) 2)
                                                  to-add (if (integer? each-side)
                                                           (int each-side)
                                                           (float each-side))]
                                              {:text (str "Add " to-add "kg to each side of the bench press bar")
                                               :estimate 5
                                               :tags ["Medium" "Home" "Fitness"]}))
                                          {:text (str "Check that bench press bar is loaded to a total of " press "kgs")
                                           :estimate 1
                                           :tags ["Low" "Home" "Fitness"]}
                                          {:text (str "Bench Press " press "kg for 5 reps")
                                           :estimate 3
                                           :tags ["High" "Home" "Fitness"]}])))
                            [{:text (str "Remove all weights from the bench press bar")
                              :estimate 1
                              :tags ["Low" "Home" "Fitness"]}
                             {:text (str "Shift bench press barbell to squat position")
                              :estimate 1
                              :tags ["Low" "Home" "Fitness"]}]))
         tasks (concat start-tasks exercises end-tasks)
         total-time (reduce + (map :estimate tasks))]
     (str "- Complete 'Workout B' @parallel(false) @autodone(true) @estimate(" (+ 15 total-time) "m) @defer(4am) @due(5pm)\n"
          (apply str (map #(str "\t" %) (map build-task tasks)))))))


(defn generate-workout-cardio
  "Generate a cardio taskpaper workout."
  []
  (let [day-of-week (-> (java.util.Date.) (.getDay))] ;; 0 = Sun, 1 = Mon, etc.
    (if (or (= day-of-week 6) (= day-of-week 0)) ;; if it's the weekend, do a long run!
      (str "- Go for a long run @parallel(false) @autodone(true) @estimate(110m) @due(5pm) @defer(4am)\n"
           "\t- Get changed into running gear @estimate(5m) @tags(Low, Home)\n"
           "\t- Put on running shoes @estimate(1m) @tags(Low, Home)\n"
           "\t- Go for a 10km run @estimate(90m) @tags(High, Home, Fitness)\n"
           "\t- Have a cold shower @estimate(5m) @tags(Low, Home, Mindfulness)\n")
      ;; if it's a weekday do a zone 2 indoor cycle
      (str "- Complete a 'Zone 2' Workout @parallel(false) @autodone(true) @estimate(120m) @due(5pm) @defer(4am)\n"
           "\t- Get changed into workout gear @estimate(5m) @tags(Low, Home)\n"
           "\t- Start workout tracking for Indoor Cycle @estimate(1m) @tags(Low, Home)\n"
           "\t- Assault Bike for 90 minutes @estimate(100m) @tags(High, Home, Fitness)\n"
           "\t- Stop workout tracking @estimate(1m) @tags(Low, Home)\n"
           "\t- Have a cold shower @estimate(5m) @tags(Low, Home, Mindfulness)\n"))))


(defn generate-workout
  "Generate a workout given a list of logs, a workout type (a or b or cardio).
   Returns a string of taskpaper-formatted text."
  ([logs] (generate-workout logs EXERCISES 85))
  ([logs exercises] (generate-workout logs exercises 85))
  ([logs exercises bodyweight]
   (let [workout-type (which-workout? logs exercises)
         main-workout (cond (= workout-type :a) (generate-workout-a logs bodyweight)
                            (= workout-type :b) (generate-workout-b logs bodyweight)
                            (= workout-type :cardio) (generate-workout-cardio))
         random-distance (+ 2 (rand-int 3))
         day-of-week (-> (java.util.Date.) (.getDay)) ;; 0 = Sun, 1 = Mon, etc.
         weekend? (or (= day-of-week 6) (= day-of-week 0))]
     (if (not (workout-type :cardio))
       (str (str "- Go for a short run @parallel(false) @autodone(true) @estimate(60m) @due(5pm) @defer(4am)\n"
                 "\t- Get changed into running gear @estimate(5m) @tags(Low, Home)\n"
                 "\t- Put on running shoes @estimate(1m) @tags(Low, Home)\n"
                 "\t- Go for a " random-distance "km run @estimate(" (+ 15 (* 6 random-distance)) "m) @tags(High, Home, Fitness)\n")
            main-workout)
       (if weekend?
         main-workout
         (str (str "- Go for a short run @parallel(false) @autodone(true) @estimate(60m) @due(5pm) @defer(4am)\n"
                   "\t- Get changed into running gear @estimate(5m) @tags(Low, Home)\n"
                   "\t- Put on running shoes @estimate(1m) @tags(Low, Home)\n"
                   "\t- Go for a " random-distance "km run @estimate(" (+ 15 (* 6 random-distance)) "m) @tags(High, Home, Fitness)\n")
              main-workout))))))




(defn generate-report
  "Generate a report of recent workouts."
  [logs exercises]
  (flatten
   (for [exercise exercises]
     (str exercise "\n"
          "\t- " "Last Done: " (days-since-exercise :logs logs :exercise exercise) " days ago\n"
          "\t- " "Estimated 1RM: " (estimate-orm :logs logs :exercise exercise) "kg 1RM\n"
          "\t- " "Level: " (measure-orm-level :exercise exercise
                                              :orm (estimate-orm :logs logs
                                                                 :exercise exercise)
                                              :gender "male"
                                              :bodyweight 82.1) "\n"))))
   





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
      :cardio {:default false
               :help "Generate a cardio workout instead of a lifting workout."
               :parse-fn :boolean}
      :n      {:default 1
               :help "Number of workouts to generate."
               :parse-fn int}
      :report {:default false
               :help "Generate a report of recent workouts."
               :parse-fn :boolean}
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
    
    (cond 
          (:report opts) (println (generate-report logs EXERCISES))
          :else (println (generate-workout logs EXERCISES bodyweight)))
    
    ;; (println (generate-workout logs EXERCISES bodyweight))
    ;; (cond
    ;;   (zero? (:n opts))
    ;;   (println "No workouts to generate.")
    ;;   :else (println (generate-workout logs)))
    ))


(-main)