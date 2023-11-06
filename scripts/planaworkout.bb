#!/usr/bin/env bb
;;
;; A small script to generate a workout plan in taskpaper format.
;; Designed to be suitable for use with OmniFocus.
;; 
;; Takes an input directory of JSON files, where each is a log of an individual
;; set of a (weight)lifting exercise. Each has the following format (example):
;; {
;;   "exercise": "Back Squat",
;;   "device_name": "Galen’s iPhone",
;;   "longitude": "149.1270023349141",
;;   "datetime": "2023-10-27T13:28:14+11:00",
;;   "kg": 35,
;;   "reps": 5,
;;   "percieved_difficulty": "8",
;;   "latitude": "-35.27072640221976",
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
   
   Use rough 'strength levels' to return a score between 0 and 5, where 1 is a
   beginner and 5 is elite."
  [& {:keys [exercise orm gender bodyweight age]}]
  (let [bodyweight (or bodyweight
                       (cond (= gender "male") 85
                             (= gender "female") 60
                             :else 70))
        fractional-orm (/ orm bodyweight)]
    (cond (= gender "male")
          (cond (= exercise "Overhead Press")
                (cond (< fractional-orm 0.35) 0
                      (< fractional-orm 0.55) 1
                      (< fractional-orm 0.8) 2
                      (< fractional-orm 1.1) 3
                      (< fractional-orm 1.4) 4
                      (>= fractional-orm 1.4) 5
                      :else nil)
                (= exercise "Bench Press")
                (cond (< fractional-orm 0.5) 0
                      (< fractional-orm 0.75) 1
                      (< fractional-orm 1.25) 2
                      (< fractional-orm 1.75) 3
                      (< fractional-orm 2) 4
                      (>= fractional-orm 2) 5
                      :else nil)
                (= exercise "Barbell Row")
                (cond (< fractional-orm 0.5) 0
                      (< fractional-orm 0.75) 1
                      (< fractional-orm 1) 2
                      (< fractional-orm 1.5) 3
                      (< fractional-orm 1.75) 4
                      (>= fractional-orm 1.75) 5
                      :else nil)
                (= exercise "Back Squat")
                (cond (< fractional-orm 0.75) 0
                      (< fractional-orm 1.25) 1
                      (< fractional-orm 1.5) 2
                      (< fractional-orm 2.25) 3
                      (< fractional-orm 2.75) 4
                      (>= fractional-orm 2.75) 5
                      :else nil)
                (= exercise "Deadlift")
                (cond (< fractional-orm 1) 0
                      (< fractional-orm 1.5) 1
                      (< fractional-orm 2) 2
                      (< fractional-orm 2.5) 3
                      (< fractional-orm 3) 4
                      (>= fractional-orm 3) 5
                      :else nil)
                (= exercise "Barbell Bicep Curl")
                (cond (< fractional-orm 0.2) 0
                      (< fractional-orm 0.4) 1
                      (< fractional-orm 0.6) 2
                      (< fractional-orm 0.85) 3
                      (< fractional-orm 1.15) 4
                      (>= fractional-orm 1.15) 5
                      :else nil)
                :else nil)
          (= gender "female")
          (cond (= exercise "Overhead Press")
                (cond (< fractional-orm 0.2) 0
                      (< fractional-orm 0.35) 1
                      (< fractional-orm 0.5) 2
                      (< fractional-orm 0.75) 3
                      (< fractional-orm 1) 4
                      (>= fractional-orm 1) 5
                      :else nil)
                (= exercise "Bench Press")
                (cond (< fractional-orm 0.25) 0
                      (< fractional-orm 0.5) 1
                      (< fractional-orm 0.75) 2
                      (< fractional-orm 1) 3
                      (< fractional-orm 1.5) 4
                      (>= fractional-orm 1.5) 5
                      :else nil)
                (= exercise "Barbell Row")
                (cond (< fractional-orm 0.25) 0
                      (< fractional-orm 0.4) 1
                      (< fractional-orm 0.65) 2
                      (< fractional-orm 0.9) 3
                      (< fractional-orm 1.2) 4
                      (>= fractional-orm 1.2) 5
                      :else nil)
                (= exercise "Back Squat")
                (cond (< fractional-orm 0.5) 0
                      (< fractional-orm 0.75) 1
                      (< fractional-orm 1.25) 2
                      (< fractional-orm 1.5) 3
                      (< fractional-orm 2) 4
                      (>= fractional-orm 2) 5
                      :else nil)
                (= exercise "Deadlift")
                (cond (< fractional-orm 0.5) 0
                      (< fractional-orm 1) 1
                      (< fractional-orm 1.25) 2
                      (< fractional-orm 1.75) 3
                      (< fractional-orm 2.5) 4
                      (>= fractional-orm 2.5) 5
                      :else nil)
                (= exercise "Barbell Bicep Curl")
                (cond (< fractional-orm 0.1) 0
                      (< fractional-orm 0.2) 1
                      (< fractional-orm 0.4) 2
                      (< fractional-orm 0.6) 3
                      (< fractional-orm 0.85) 4
                      (>= fractional-orm 0.85) 5
                      :else nil)
                :else nil)
          :else (int (median [(measure-orm-level :exercise exercise
                                                 :orm orm
                                                 :bodyweight bodyweight
                                                 :gender "male")
                              (measure-orm-level :exercise exercise
                                                 :orm orm
                                                 :bodyweight bodyweight
                                                 :gender "female")])))))


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
   
   We take the most recent 10 sets, and use the average weight & reps for them."
  [& {:keys [logs exercise]}]
  (let [recent-logs (->> logs
                         (filter #(= (:exercise %) exercise)) ;; get logs for the exercise
                         (sort-by :timestamp)
                         (take-last 10))
        recent-weights (map :kg recent-logs)
        recent-reps (map :reps recent-logs)
        recent-orms (map #(* % (+ 1 (/ % 30))) recent-weights)]
    (int (average [(average recent-orms) (median recent-orms)]))))


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
   
   To get this, we:
   1. Estimate your 1RM for the exercise (using 'estimate-orm').
   2. Get a recent average of weight used for the exercise.
   3. Get number of days since the exercise was last done.
   4. Discount the 1RM estimate by a factor based on days since last done.
        (This is to account for strength loss & detraining & reduce risk.)
   5. Take the mean of the discounted 1RM and the recent weight used.
   6. Discount the result to account for the target (5% less for each rep >1).
   7. Round the result of (6) to the nearest whole number.
   8. Get the latest weight used for the target rep count (if any).
   9. Average the result of (7) and (8).
   10. If the result is less than 20kg, use 20kg instead.

   However, if we're not averaging a reasonable average number of sets per day,
   we discount further to enable safe training for form.
   
   If bodyweight & gender are given, we also use the 'strength levels' to
   determine a suggested weight."
  [& {:keys [logs exercise target-reps bodyweight gender age]}]
  (let [est-orm (estimate-orm :logs logs :exercise exercise)
        recent-weight (recent-weight :logs logs :exercise exercise)
        days-since (days-since-exercise :logs logs :exercise exercise)
        discount-factor (if (< days-since 7) 1
                            ;; discount by 4.5% for each day since last done
                            (- 1 (* (/ 45 1000) (- days-since 7))))
        discounted-orm (* est-orm discount-factor)
        avg-weight (average [discounted-orm recent-weight])
        discounted-weight (* avg-weight (- 0.9 (* 0.05 (- target-reps 5))))
        rounded-weight (int discounted-weight)
        latest (latest-weight :logs logs
                              :exercise exercise
                              :target-reps target-reps)
        best-guess (if latest
                     (int (average [rounded-weight latest
                                    (median [rounded-weight latest])]))
                     rounded-weight)
        form-safe (if (or (< (sets-per-day :logs logs :exercise exercise)
                             (cond (= exercise "Back Squat") (/ 12 7)
                                   (= exercise "Deadlift") (/ 3 7)
                                   :else (/ 8 7)))
                          (> (sets-per-day :logs logs :exercise exercise) 3))
                    (int (* 0.8 best-guess)) ;; discount by 20%
                    best-guess)
        final-weight (if (< form-safe 20) 20 form-safe)
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
  [logs]
  (let [squat-weight (suggest-weight :logs logs
                                     :exercise "Back Squat"
                                     :target-reps 5)
        warmup-squat-weight (min (max (int (* 0.5 squat-weight)) 20) 60)
        press-weight (suggest-weight :logs logs
                                     :exercise "Overhead Press"
                                     :target-reps 5)
        warmup-press-weight (min (max (int (* 0.5 press-weight)) 20) 30)
        n-pullups (min (max (- (int (/ press-weight 10)) 1) 1) 5)
        deadlift-weight (suggest-weight :logs logs
                                        :exercise "Deadlift"
                                        :target-reps 5)
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
         "\t- Load squat bar to a total of " squat-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Load overhead press bar to a total of " press-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
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
         "\t- Load deadlift bar to a total of " deadlift-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Deadlift " deadlift-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Stop workout tracking for Traditional Weightlifting @estimate(1m) @tags(Low, Home)\n"
         "\t- Have a cold shower @estimate(5m) @tags(Low, Home)\n")))


(defn generate-workout-b
  "Generate a default 'Workout B' taskpaper based on logs."
  [logs]
  ;; WORKOUT B
  ;; (warmup sets, then...)
;; 5 sets of ->
;;   "Back Squat Xkg for Y reps"
;;   "Do 1 pullup"
;; 5 sets of ->
;;   "Bench Press Zkg for W reps"
;;   "Barbell Row Qkg for R reps"
;; 1 set of ->
;;   "Barbell Bicep Curl Skg for T reps"
  (let [squat-weight (suggest-weight :logs logs
                                     :exercise "Back Squat"
                                     :target-reps 5)
        warmup-squat-weight (min (max (int (* 0.5 squat-weight)) 20) 60)
        press-weight (suggest-weight :logs logs
                                     :exercise "Bench Press"
                                     :target-reps 5)
        warmup-press-weight (min (max (int (* 0.5 press-weight)) 20) 30)
        row-weight (suggest-weight :logs logs :exercise "Barbell Row" :target-reps 5)
        n-pullups (min (max (- (int (/ press-weight 10)) 2) 1) 5)
        curl-weight (suggest-weight :logs logs
                                    :exercise "Barbell Bicep Curl"
                                    :target-reps 5)]
    (str "- Complete 'Workout B' @parallel(false) @autodone(true) @estimate(120m)\n"
         "\t- Get changed into workout gear @estimate(5m) @tags(Low, Home)\n"
         "\t- Start workout tracking for Traditional Weightlifting @estimate(1m) @tags(Low, Home)\n"
         "\t- Load squat bar to a total of " warmup-squat-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Back Squat " warmup-squat-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Do 1 pullup @estimate(1m) @tags(High, Home)\n"
         (apply str
                (into []
                      (for [i (range 1 6)]
                        (str "\t- Back Squat " squat-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
                             (if (> n-pullups 1)
                               (str "\t- Do " n-pullups " pullups @estimate(1m) @tags(High, Home)\n")
                               (str "\t- Do 1 pullup @estimate(1m) @tags(High, Home)\n"))))))
         "\t- Load bench press bar to a total of " warmup-press-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
         "\t- Bench Press " warmup-press-weight "kg for 5 reps @estimate(3m) @tags(High, Home)\n"
         "\t- Load bench press bar to a total of " press-weight "kgs @estimate(5m) @tags(Medium, Home)\n"
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
  ([logs] (generate-workout logs EXERCISES))
  ([logs exercises]
   (let [workout-type (which-workout logs exercises)
         primary-workout (cond (= workout-type :a) (generate-workout-a logs)
                               (= workout-type :b) (generate-workout-b logs)
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
   :bodyweight {:default nil
                :help "Your bodyweight in kg."
                :parse-fn int}
   :gender {:default nil
            :help "Your gender (opts: male or female)"
            :parse-fn str}
   :cardio {:default false
            :help "Generate a cardio workout instead of a lifting workout."
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
                 read-logs)]
    ;; ;; QUICK REPORT
    ;; (doseq [exercise EXERCISES]
    ;;   (println exercise)
    ;;   (println "  Estimated 1RM: " (estimate-orm :logs logs :exercise exercise))
    ;;   (println "  1RM Score: " (measure-orm-level :exercise exercise
    ;;                                               :orm (estimate-orm :logs logs :exercise exercise)
    ;;                                               :bodyweight (:bodyweight opts)
    ;;                                               :gender (:gender opts)))
    ;;   (println "  Days since last exercise: " (days-since-exercise :logs logs :exercise exercise))
    ;;   (println "  Suggested weight for 5 reps: " (suggest-weight :logs logs :exercise exercise :target-reps 5))
    ;;   (println "  Average sets per day: " (sets-per-day :logs logs :exercise exercise))
    ;;   (println ""))
    (if (:cardio opts)
      (println (generate-workout-cardio))
      (println (generate-workout logs)))))

(-main)