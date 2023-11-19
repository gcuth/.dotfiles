#!/usr/bin/env bb
;;
;; A small script to generate a workout plan in taskpaper format.
;; Designed to be suitable for use with OmniFocus.
;; 
;; Takes an input directory of JSON files, where each is a log of an individual
;; set of a (weight)lifting exercise. Each has the following format (example):
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
        thresholds (cond
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
                     :else nil)
        get-score (fn [thresholds fractional-orm]
                    (let [levels (map-indexed vector thresholds)
                          find-level (fn [levels]
                                       (first (drop-while #(<= fractional-orm (second %)) levels)))]
                      (if-let [[level lower-bound] (find-level levels)]
                        (+ level
                           (if (zero? level)
                             0
                             (let [prev-bound (nth thresholds (dec level))]
                               (/ (- fractional-orm prev-bound) (- lower-bound prev-bound)))))
                        5)))]
    (if thresholds
      (get-score (thresholds exercise) fractional-orm)
      (average [(measure-orm-level :exercise exercise
                                   :orm orm
                                   :bodyweight bodyweight
                                   :gender "male")
                (measure-orm-level :exercise exercise
                                   :orm orm
                                   :bodyweight bodyweight
                                   :gender "female")])))


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
  "Given a list of logs, an exercise, and a target rep count (passedas named
   args), return the most recent weight used for that exercise at that rep count.
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
   where w is the weight used, and r is the number of reps.
   
   "
  [& {:keys [logs exercise]}]
  (let [recent-logs (->> logs
                         (filter #(= (:exercise %) exercise)) ;; get logs for the exercise
                         (sort-by :timestamp))
        recent-weights (map :kg recent-logs) ;; get weights for each of the sets
        recent-reps (map :reps recent-logs) ;; get reps for each of the sets
        recent-orms (map #(int (* % (+ 1 (/ %2 30)))) recent-weights recent-reps) ;; calculate the orm for each set
        ]
    (->> recent-orms
         (ewma) ;; calculate the ewma of the orms
         (last) ;; return the last (most recent) ewma value
         (int)))) ;; round to the nearest integer


(defn sets-per-day
  "Given a list of logs & an exercise, return the average number of sets per
   day for that exercise. To do this, for each log of a set, get the number of
   days since it was first performed. Then, count the total number of sets ever
   performed and divide by the number of days since the first set was performed."
  [& {:keys [logs exercise]}]
  (let [now (quot (System/currentTimeMillis) 1000)
        total-sets (count (filter #(= (:exercise %) exercise) logs))
        days-since-first (->> logs
                              (filter #(= (:exercise %) exercise)) ;; get logs
                              (map :timestamp) ;; get timestamps for each set
                              (map #(quot % 1000)) ;; strip milliseconds
                              (map #(- now %)) ;; subtract from current timestamp
                              (map #(quot % 86400)) ;; convert to days
                              (sort)
                              (reverse)
                              (first))]
    (/ total-sets days-since-first)))


(defn suggest-weight
  "Given a list of logs, a target rep count, & an exercise (as named args),
   return an integer value for the suggested weight.
"
  [& {:keys [logs exercise target-reps bodyweight gender age]}]
  (let [est-orm (estimate-orm :logs logs :exercise exercise) ;; estimated 1RM for the exercise (using Epley formula)
        days-since (days-since-exercise :logs logs :exercise exercise)
        discount-factor (if (< days-since 7) 1
                            ;; discount by 4.5% for each day since last done
                            (- 1 (* (/ 45 1000) (- days-since 7))))
        discounted-orm (* est-orm discount-factor) ;; estimated orm discounted by days since last done
        discounted-weight (* discounted-orm (- 0.75 (* 0.05 (- target-reps 4))))
        latest (* (latest-weight :logs logs :exercise exercise :target-reps target-reps)
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
                                  (* 5 (inc (quot form-safe 5))))) ;; round up to nearest 5
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

(defn which-workout
  "Given a list of logs, decides which workout to do next. Returns a keyword.
   Either: :a, :b, or :cardio."
  ([logs] (which-workout logs EXERCISES))
  ([logs exercises]
   (let [days-since-last-lift (days-since-last-lifting-workout logs exercises)
         days-since-a (days-since-last-lifting-workout logs ["Overhead Press"])
         days-since-b (days-since-last-lifting-workout logs ["Bench Press"])]
     ;; if you've done more than 4 lifting days in the last 7, or 8 in the last
      ;; 14, or 12 in the last 21, or 16 in the last 28, do cardio.
     (cond (>= (count-lifting-days-in-n-days logs 7) 4) :cardio
           (>= (count-lifting-days-in-n-days logs 14) 8) :cardio
           (>= (count-lifting-days-in-n-days logs 21) 12) :cardio
           (>= (count-lifting-days-in-n-days logs 28) 16) :cardio
           (> days-since-a days-since-b) :a
           :else :b))))

(defn generate-workout-a
  "Generate a default 'Workout A' taskpaper based on logs."
  [logs bodyweight]
  (let [squat-weight (suggest-weight :logs logs
                                     :exercise "Back Squat"
                                     :target-reps 5
                                     :bodyweight bodyweight)
        warmup-squat-weight (min (max (int (* 0.5 squat-weight)) 20) 60)
        press-weight (suggest-weight :logs logs
                                     :exercise "Overhead Press"
                                     :target-reps 5
                                     :bodyweight bodyweight)
        warmup-press-weight (min (max (int (* 0.5 press-weight)) 20) 30)
        n-pullups (min (max (- (int (/ press-weight 10)) 1) 1) 5)
        deadlift-weight (suggest-weight :logs logs
                                        :exercise "Deadlift"
                                        :target-reps 5
                                        :bodyweight bodyweight)
        warmup-deadlift-weight (min (max (int (* 0.5 deadlift-weight)) 40) 80)]
    (str "- Complete 'Workout A' @parallel(false) @autodone(true) @estimate(120m)\n"
         "\t- Get changed into workout gear @estimate(5m) @tags(Low, Home)\n"
         "\t- Start workout tracking for Traditional Weightlifting @estimate(1m) @tags(Low, Home)\n"
         "\t- Load squat bar to a total of " warmup-squat-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Load overhead press bar to a total of " warmup-press-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Back Squat " warmup-squat-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Dead hang for 10 seconds @estimate(1m) @tags(High, Home)\n"
         "\t- Overhead Press " warmup-press-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Do 1 pullup @estimate(1m) @tags(High, Home)\n"
         (if (> (- squat-weight warmup-squat-weight) 0)
           (str
            "\t- Add " (- squat-weight warmup-squat-weight) "kg to squat bar (for a total of " squat-weight "kgs) @estimate(5m) @tags(Medium, Home)\n"))
         "\t- Add " (- press-weight warmup-press-weight) "kg to overhead press bar (for a total of " press-weight "kgs) @estimate(5m) @tags(Medium, Home)\n"
         (apply str
                (into []
                      (for [i (range 1 6)]
                        (str "\t- Back Squat " squat-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
                             "\t- Dead hang for 10 seconds @estimate(1m) @tags(High, Home)\n"
                             "\t- Overhead Press " press-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
                             (if (> n-pullups 1)
                               (str "\t- Do " n-pullups " pullups @estimate(1m) @tags(High, Home)\n")
                               (str "\t- Do 1 pullup @estimate(1m) @tags(High, Home)\n"))))))
         "\t- Load deadlift bar to a total of " warmup-deadlift-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Deadlift " warmup-deadlift-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Add " (- deadlift-weight warmup-deadlift-weight) "kg to deadlift bar (for a total of " deadlift-weight "kgs) @estimate(5m) @tags(Medium, Home)\n"
         "\t- Deadlift " deadlift-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Stop workout tracking for Traditional Weightlifting @estimate(1m) @tags(Low, Home)\n"
         "\t- Have a cold shower @estimate(5m) @tags(Low, Home)\n")))


(defn generate-workout-b
  "Generate a default 'Workout B' taskpaper based on logs."
  [logs bodyweight]
  (let [squat-weight (suggest-weight :logs logs
                                     :exercise "Back Squat"
                                     :target-reps 5
                                     :bodyweight bodyweight)
        warmup-squat-weight (min (max (int (* 0.5 squat-weight)) 20) 60)
        press-weight (suggest-weight :logs logs
                                     :exercise "Bench Press"
                                     :target-reps 5
                                     :bodyweight bodyweight)
        warmup-press-weight (min (max (int (* 0.5 press-weight)) 20) 30)
        row-weight (suggest-weight :logs logs :exercise "Barbell Row" :target-reps 5 :bodyweight bodyweight)
        n-pullups (min (max (- (int (/ press-weight 10)) 2) 1) 5)
        curl-weight (suggest-weight :logs logs
                                    :exercise "Barbell Bicep Curl"
                                    :target-reps 5
                                    :bodyweight bodyweight)]
    (str "- Complete 'Workout B' @parallel(false) @autodone(true) @estimate(120m)\n"
         "\t- Get changed into workout gear @estimate(5m) @tags(Low, Home)\n"
         "\t- Start workout tracking for Traditional Weightlifting @estimate(1m) @tags(Low, Home)\n"
         "\t- Load squat bar to a total of " warmup-squat-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Back Squat " warmup-squat-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Do 1 pullup @estimate(1m) @tags(High, Home)\n"
         (if (> (- squat-weight warmup-squat-weight) 0)
           (str
            "\t- Add " (- squat-weight warmup-squat-weight) "kg to squat bar (for a total of " squat-weight "kgs) @estimate(5m) @tags(Medium, Home)\n"))
         (apply str
                (into []
                      (for [i (range 1 6)]
                        (str "\t- Back Squat " squat-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
                             (if (> n-pullups 1)
                               (str "\t- Do " n-pullups " pullups @estimate(1m) @tags(High, Home)\n")
                               (str "\t- Do 1 pullup @estimate(1m) @tags(High, Home)\n"))))))
         "\t- Load bench press bar to a total of " warmup-press-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Bench Press " warmup-press-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Add " (- press-weight warmup-press-weight) "kg to bench press bar (for a total of " press-weight "kgs) @estimate(5m) @tags(Medium, Home)\n"
         "\t- Load barbell row bar to a total of " row-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         (apply str
                (into []
                      (for [i (range 1 6)]
                        (str "\t- Bench Press " press-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
                             "\t- Barbell Row " row-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"))))
         "\t- Load barbell bicep curl bar to a total of " curl-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Barbell Bicep Curl " curl-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Stop workout tracking for Traditional Weightlifting @estimate(1m) @tags(Low, Home)\n"
         "\t- Have a cold shower @estimate(5m) @tags(Low, Home)\n")))


(defn generate-workout-cardio
  "Generate a cardio taskpaper. If it's the weekend (Sat or Sun), do a 10km run.
   Otherwise, choose randomly between a short run (3-5km) and an indoor cycle."
  []
  (let [day-of-week (-> (java.util.Date.) (.getDay)) ;; 0 = Sun, 1 = Mon, etc.
        random-distance (+ 2 (rand-int 3))] ;; 3-5km run distance
    (if (or (= day-of-week 6) (= day-of-week 0)) ;; if it's the weekend, do a 10km run!
      (str "- Go for a long run @parallel(false) @autodone(true) @estimate(110m)\n"
           "\t- Get changed into running gear @estimate(5m) @tags(Low, Home)\n"
           "\t- Put on running shoes @estimate(1m) @tags(Low, Home)\n"
           "\t- Go for a 10km run @estimate(90m) @tags(High, Home)\n"
           "\t- Have a cold shower @estimate(5m) @tags(Low, Home)\n")
      ;; if it's a weekday, choose randomly between a short run and an indoor cycle
      (rand-nth [(str "- Complete a 'Zone 2' Workout @parallel(false) @autodone(true) @estimate(120m)\n"
                      "\t- Get changed into workout gear @estimate(5m) @tags(Low, Home)\n"
                      "\t- Start workout tracking for Indoor Cycle @estimate(1m) @tags(Low, Home)\n"
                      "\t- Assault Bike for 90 minutes @estimate(100m) @tags(High, Home)\n"
                      "\t- Stop workout tracking @estimate(1m) @tags(Low, Home)\n"
                      "\t- Have a cold shower @estimate(5m) @tags(Low, Home)\n")
                 (str "- Go for a short run @parallel(false) @autodone(true) @estimate(60m)\n"
                      "\t- Get changed into running gear @estimate(5m) @tags(Low, Home)\n"
                      "\t- Put on running shoes @estimate(1m) @tags(Low, Home)\n"
                      "\t- Go for a " random-distance "km run @estimate(" (+ 15 (* 6 random-distance)) "m) @tags(High, Home)\n"
                      "\t- Have a cold shower @estimate(5m) @tags(Low, Home)\n")]))))

(defn generate-workout
  "Generate a workout given a list of logs, a workout type (a or b or cardio).
   Returns a string of taskpaper-formatted text. If it's a lifting workout, also
   add cardio at the end ~25% of the time."
  ([logs] (generate-workout logs EXERCISES 85))
  ([logs exercises] (generate-workout logs exercises 85))
  ([logs exercises bodyweight]
   (let [workout-type (which-workout logs exercises)
         primary-workout (cond (= workout-type :a) (generate-workout-a logs bodyweight)
                               (= workout-type :b) (generate-workout-b logs bodyweight)
                               (= workout-type :cardio) (generate-workout-cardio))]
     (if (= workout-type :cardio)
       primary-workout
       (str primary-workout
            (if (< (rand) 0.25)
              (generate-workout-cardio)
              ""))))))


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
      (zero? (:n opts))
      (println "No workouts to generate.")
      (> (:n opts) 1) ;; if more than 1, cycle through workout A, B, cardio
      (println (str/join "\n"
                         (take (:n opts)
                               (cycle [(generate-workout-a logs bodyweight)
                                       (generate-workout-cardio)
                                       (generate-workout-b logs bodyweight)
                                       (generate-workout-cardio)]))))
      :else ;; otherwise, just generate one workout
      (if (:cardio opts)
        (println (generate-workout-cardio))
        (println (generate-workout logs))))))

  (-main)