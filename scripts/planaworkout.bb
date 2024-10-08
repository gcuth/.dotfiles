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


(defn read-lifting-logs
  "Read a list of JSON file paths into a list of maps."
  [files]
  (->> files
       (map #(json/parse-string (slurp %) true))
       (map #(assoc % :timestamp
                    (.getTime (inst/read-instant-date (:datetime %)))))
       (map #(assoc % :date
                    (first (str/split (:datetime %) #"T"))))))


(defn read-run-log
  "Read a CSV file (representing a log of runs) into a list of maps; return nil
   if the file does not exist."
  [path]
  (if (fs/exists? path)
    (->> (slurp path)
         (str/split-lines)
         (rest)
         (map #(zipmap [:date :time :distance :pace] (str/split % #","))))
    nil))


(defn days
  "Generate a list of days in the format 'YYYY-MM-DD' for the next n days."
  [n]
  (let [fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")
        today (java.time.LocalDate/now)]
    (map #(str (.format (.plusDays today %) fmt)) (range n))))


(defn today
  "Get the current date in the format 'YYYY-MM-DD'."
  []
  (first (days 1)))


(defn days-between
  "Calculate the number of days between two dates given as YYYY-MM-DD strings."
  [start end]
  (let [start-date (java.time.LocalDate/parse start)
        end-date (java.time.LocalDate/parse end)]
    (Math/abs (.between java.time.temporal.ChronoUnit/DAYS
                        start-date end-date))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;; STANDARD EXERCISES & THEIR REQUIREMENTS ;;;;;;;;;;;;;;;;;;;
;;;;;; (if provided, standards include ORM based on bodyweight & gender) ;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def EXERCISES
  [{:name "Overhead Press"
    :equipment ["Barbell"
                "Squat Rack"
                "Bumper Plates"
                "Fractional Plates"
                "Barbell Collars"]
    :minimum-kg 20
    :plates [0.5 1 1.5 5 10 15 20]
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Front Deltoids"]
              :secondary ["Triceps" "Lateral Deltoids"]}
    :standards {:male [0.35 0.55 0.8 1.1 1.4]
                :female [0.2 0.35 0.5 0.75 1]}
    :tags nil}
   {:name "Bench Press"
    :equipment ["Barbell"
                "Bench"
                "Bumper Plates"
                "Fractional Plates"
                "Barbell Collars"]
    :minimum-kg 20
    :plates [0.5 1 1.5 5 10 15 20]
    :defaults {:reps 10
               :sets 5}
    :muscles {:primary ["Chest" "Front Deltoids"]
              :secondary ["Triceps"]}
    :standards {:male [0.5 0.75 1.25 1.75 2]
                :female [0.25 0.5 0.75 1 1.5]}
    :tags ["Powerlifting"]}
   {:name "Back Squat"
    :equipment ["Barbell"
                "Squat Rack"
                "Bumper Plates"
                "Fractional Plates"
                "Barbell Collars"]
    :minimum-kg 20
    :plates [0.5 1 1.5 5 10 15 20]
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Quads" "Adductors" "Glutes" "Lower Back"]
              :secondary ["Calves"]}
    :standards {:male [0.75 1.25 1.5 2.25 2.75]
                :female [0.5 0.75 1.25 1.5 2]}
    :tags ["Powerlifting"]}
   {:name "Deadlift"
    :equipment ["Barbell"
                "Trap Bar"
                "Bumper Plates"
                "Fractional Plates"
                "Barbell Collars"]
    :minimum-kg 20
    :plates [0.5 1 1.5 5 10 15 20]
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Glutes" "Lower Back" "Quads"]
              :secondary ["Adductors" "Trapezius"
                          "Forearm Flexors" "Hamstrings"]}
    :standards {:male [1 1.5 2 2.5 3]
                :female [0.5 1 1.25 1.75 2.5]}
    :tags ["Powerlifting"]}
   {:name "Barbell Row"
    :equipment ["Barbell"
                "Bumper Plates"
                "Fractional Plates"
                "Barbell Collars"]
    :minimum-kg 20
    :plates [0.5 1 1.5 5 10 15 20]
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Lats" "Trapezius" "Rear Deltoids"]
              :secondary ["Biceps" "Lower Back"
                          "Forearm Flexors" "Rotator Cuffs"]}
    :standards {:male [0.5 0.75 1 1.5 1.75]
                :female [0.25 0.4 0.65 0.9 1.2]}
    :tags nil}
   {:name "Barbell Bicep Curl"
    :equipment ["Barbell"
                "Bumper Plates"
                "Fractional Plates"
                "Barbell Collars"]
    :minimum-kg 20
    :plates [0.5 1 1.5 5 10 15 20]
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Biceps"]
              :secondary ["Forearm Flexors"]}
    :standards {:male [0.2 0.4 0.6 0.85 1.15]
                :female [0.1 0.2 0.4 0.6 0.85]}
    :tags nil}
   {:name "Pullups"
    :equipment ["Pullup Bar"]
    :minimum-kg nil
    :plates []
    :defaults {:reps 5
               :sets 3}
    :muscles {:primary ["Lats"]
              :secondary ["Biceps" "Rear Deltoids"
                          "Forearm Flexors" "Rotator Cuffs"]}
    :tags ["Bodyweight"]}
   {:name "Pushups"
    :equipment nil
    :minimum-kg nil
    :plates []
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Chest" "Front Deltoids"]
              :secondary ["Triceps" "Abs"]}
    :tags ["Bodyweight"]}
   {:name "Situps"
    :equipment nil
    :minimum-kg nil
    :defaults {:reps 20
               :sets 3}
    :muscles {:primary ["Abs"]
              :secondary ["Obliques"]}
    :tags ["Bodyweight"]}
   {:name "Flutter Kicks"
    :equipment nil
    :minimum-kg nil
    :defaults {:reps 20
               :sets 3}
    :muscles {:primary ["Abs"]
              :secondary ["Obliques" "Hip Flexors"]}
    :tags ["Bodyweight" "Cardio"]}
   {:name "Mountain Climbers"
    :equipment nil
    :minimum-kg nil
    :defaults {:reps 20
               :sets 3}
    :muscles {:primary ["Abs"]
              :secondary ["Obliques"]}
    :tags ["Bodyweight" "Cardio"]}
   {:name "Dips"
    :equipment ["Dip Bars"]
    :minimum-kg nil
    :plates []
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Chest" "Front Deltoids"]
              :secondary ["Triceps"]}
    :tags ["Bodyweight"]}
   {:name "Ring Rows"
    :equipment ["Pullup Bar"
                "Rings"]
    :minimum-kg nil
    :plates []
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Lats" "Trapezius" "Rear Deltoids"]
              :secondary ["Biceps" "Forearm Flexors" "Rotator Cuffs"]}
    :tags ["Bodyweight"]}
   {:name "Hanging Leg Raise"
    :equipment ["Pullup Bar"]
    :minimum-kg nil
    :plates []
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Abs"]
              :secondary ["Obliques"]}
    :tags ["Bodyweight"]}
   {:name "Tibialis Raise"
    :equipment ["Bench"
                "Tib Bar"]
    :minimum-kg 10
    :plates [0.5 1 1.5 5 10 15 20]
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary []
              :secondary []}
    :tags []}
   {:name "Jefferson Curl"
    :equipment ["Barbell"
                "Bumper Plates"
                "Fractional Plates"
                "Barbell Collars"]
    :minimum-kg 20
    :plates [0.5 1 1.5 5 10 15 20]
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Lower Back"]
              :secondary ["Adductors" "Glutes" "Hamstrings" "Trapezius"]}
    :tags []}
   {:name "Single Leg Calf Raise"
    :equipment ["Bench"
                "Kettlebells"]
    :minimum-kg 8
    :allowed-kg [2 4 8 12 16 20]
    :defaults {:reps 10
               :sets 3}
    :muscles {:primary ["Calves"]
              :secondary []}
    :tags []}
   {:name "Nordic Curl"
    :equipment ["Bench"]
    :minimum-kg nil
    :plates []
    :muscles {:primary ["Hamstrings"]
              :secondary ["Glutes" "Lower Back"]}
    :tags ["Bodyweight"]}
   {:name "Kettlebell Shrug"
    :equipment ["Kettlebells"]
    :minimum-kg nil ;; TODO: add a minimum weight
    :plates nil
    :defaults {:reps 20
               :sets 3}
    :allowed-kg [2 4 8 12 16 20]
    :muscles {:primary ["Trapezius"]
              :secondary ["Forearm Flexors"]}
    :tags []}
   {:name "Kettlebell Lateral Raise"
    :equipment ["Kettlebells"]
    :minimum-kg nil ;; TODO: add a minimum weight
    :plates nil
    :defaults {:reps 10
               :sets 3}
    :allowed-kg [2 4 8 12 16 20]
    :muscles {:primary ["Lateral Deltoids"]
              :secondary ["Front Deltoids"]}
    :tags []}
   {:name "Kettlebell Front Raise"
    :equipment ["Kettlebells"]
    :minimum-kg nil ;; TODO: add a minimum weight
    :plates nil
    :defaults {:reps 10
               :sets 3}
    :allowed-kg [2 4 8 12 16 20]
    :muscles {:primary ["Front Deltoids"]
              :secondary ["Lateral Deltoids"]}
    :tags []}
   {:name "Kettlebell Overhead Press"
    :equipment ["Kettlebells"]
    :minimum-kg nil ;; TODO: add a minimum weight
    :plates nil
    :defaults {:reps 10
               :sets 3}
    :allowed-kg [2 4 8 12 16 20]
    :muscles {:primary ["Front Deltoids"]
              :secondary ["Triceps" "Lateral Deltoids"]}
    :tags []}
   {:name "Kettlebell Bicep Curl"
    :equipment ["Kettlebells"]
    :minimum-kg nil ;; TODO: add a minimum weight
    :plates nil
    :defaults {:reps 20
               :sets 3}
    :allowed-kg [2 4 8 12 16 20]
    :muscles {:primary ["Biceps"]
              :secondary ["Forearm Flexors"]}
    :tags []}
   ;; NECK STRENGTH/SAFETY EXERCISES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   {:name "Front Neck Curl"}
   {:name "Back Neck Curl"}])

(def WORKOUTS ;; list of 'standard' workouts (used with EXERCISES to generate)
  [{:name ""}
   
   {:name "Couch to 5K"}])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;; RUNNING ANALYTICS & UTILS ;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn average-daily-kms
  "Given a run log (as list of maps), return the average kms run per day.
   (This takes into account all days between first and last run in the log.)"
  [runs]
  (let [distances (map :distance runs) ;; get distances for each run 
        distances (map #(Float/parseFloat %) distances) ;; as floats
        total-kms (reduce + distances) ;; sum the total kms in the entire log
        total-days (days-between (first (map :date runs))
                                 (last (map :date runs)))]
    (/ total-kms total-days)))


(defn average-kms-when-run
  "Given a run log (as list of maps), return the average kms run per workout."
  [runs]
  (let [distances (map :distance runs) ;; get distances for each run
        distances (map #(Float/parseFloat %) distances) ;; as floats
        total-kms (reduce + distances) ;; sum the total kms in the entire log
        total-runs (count distances)]
    (/ total-kms total-runs)))


(defn recent-kms
  "Given a run log (as list of maps), return an estimate of the recent kms run.
   Calculated as the average of the last n runs in the log (default 21)."
  ([runs] (recent-kms runs 21))
  ([runs n]
   (let [distances (map :distance runs) ;; get distances for each run
         distances (map #(Float/parseFloat %) distances) ;; as floats
         recent-distances (take n (reverse distances)) ;; get last n distances
         total-kms (reduce + recent-distances) ;; sum total kms in recent log
         total-runs (count recent-distances)]
     (/ total-kms total-runs))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;; EXERCISE SUGGESTION ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn days-since-exercise
  "Given a list of lifting logs & a specific exercise (passed as named args),
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
  "Given a list of lifting logs & an exercise (passed as named args), return an
   integer value of the recent weight used for that exercise (using ewma)."
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
    (if (empty? recent-orms)
      0
      (->> recent-orms
           (ewma) ;; calculate the ewma of the orms
           (last) ;; return the last (most recent) ewma value
           (int))))) ;; round to the nearest integer


(defn discount-orm
  "Discount an estimated 1RM for a target rep count."
  [orm reps]
  (* orm (- 1 (/ reps 30))))


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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;; WORKOUT GENERATION UTILS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn which-workout?
  "Given a date as a YYYY-MM-DD string, return a keyword representing the
   workout type for that day. The year cycles through a sequence of workouts
   on a fortnightly basis (with long run always on Saturdays!)."
  [date]
  (let [sequence [:run :upper :hiit :lower :short-run :long-run :upper
                  :run :lower :hiit :upper :short-run :long-run :lower]
        day-of-week (mod (- (days-between "2023-01-01" date) 1) 14)]
    (nth sequence day-of-week)))


(defn strength-targets
  "Given a list of logs, return a map of realistic current set/rep targets for
   all major exercises based on recent performance (if available). "
  [& {:keys [logs bodyweight]}]
  (let [target (fn [logs exercise reps]
                 ;; randomly add 1kg every 1/20th of the time
                 (+ (if (zero? (mod (rand-int 20) 20)) 1 0)
                    (int (discount-orm
                          (estimate-orm :logs logs :exercise exercise)
                          reps))))]
    {:tibialis {:kg (or (target logs "Tibialis Raises" 10) 0)
                :reps 10}
     :calf {:kg (or (target logs "Single Leg Calf Raises" 10) 0)
            :reps 10}
     :squat {:kg (or (target logs "Back Squat" 10) 20)
             :reps 10}
     :split {:kg (or (target logs "Split Squat" 10) 0)
             :reps 10}
     :jefferson {:kg (or (target logs "Jefferson Curl" 10) 0)
                 :reps 10}
     :bench {:kg (or (target logs "Bench Press" 10) 20)
             :reps 10}
     :overhead {:kg (or (target logs "Overhead Press" 10) 20)
                :reps 10}
     :pullups {:kg 0
               :reps (recent-reps :logs logs :exercise "Pullups")}
     :deadlift {:kg (or (target logs "Deadlift" 10) 40)
                :reps 10}}))


(defn build-task
  "Given a map, build a taskpaper task."
  [task]
  (str "- " (:text task)
       (if (:estimate task) (str " @estimate(" (:estimate task) "m)") "")
       (if (:tags task) (str " @tags(" (str/join ", " (:tags task)) ")") "")
       "\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; TASKS/LISTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;; (defined task lists for standard workouts & recovery processes) ;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn generate-pre-running-tasks
  []
  [{:text "Get changed into running gear" :estimate 5 :tags ["Low" "Home"]}
   {:text "Put on running shoes" :estimate 5 :tags ["Low" "Home"]}])


(defn generate-running-tasks
  [& {:keys [distance pace] :or {distance 5 pace 6}}]
  [{:text (str "Go for a " distance "km run") :tags ["High" "Fitness"]
    :estimate (+ (* distance pace) 5)}])


(defn generate-pre-hiking-tasks
  []
  [{:text "Get changed into hiking gear" :estimate 5 :tags ["Low" "Home"]}
   {:text "Put on hiking boots" :estimate 5 :tags ["Low" "Home"]}
   {:text "Pack a hiking backpack with water and snacks"
    :estimate 5 :tags ["Low" "Home"]}])


(defn generate-hike-tasks
  "Generate a list of all hike workout tasks for a given distance (km) and
   pace (mins per km). If no pace is given, assume 20 mins per km."
  ([& {:keys [distance pace] :or {distance 5 pace 20}}]
   [{:text (str "Go for a " distance "km outdoor hike")
     :tags ["High" "Outside" "Nature" "Fitness"]
     :estimate (+ (* distance pace) 5)}]))


(defn generate-post-cardio-tasks
  []
  [{:text "Stretch to touch toes on slant board for 2 minutes"
    :estimate 3 :tags ["Medium" "Home" "Fitness"]}
   {:text "Calf stretch on slant board for 2 minutes"
    :estimate 3 :tags ["Medium" "Home" "Fitness"]}
   {:text "Incline pigeon pose for 2 minutes (each side)"
    :estimate 5 :tags ["Medium" "Home" "Fitness"]}
   {:text "Couch (quad) stretch for 2 minutes (each side)"
    :estimate 5 :tags ["Medium" "Home" "Fitness"]}
   {:text "Butterfly pose for 2 minutes"
    :estimate 3 :tags ["Medium" "Home" "Fitness"]}])


(defn generate-pre-lift-stretch-tasks
  []
  [{:text "Stretch to touch toes on slant board for 2 minutes"
    :estimate 3 :tags ["Medium" "Home" "Fitness"]}
   {:text "Calf stretch on slant board for 2 minutes"
    :estimate 3 :tags ["Medium" "Home" "Fitness"]}])


(defn generate-post-lift-stretch-tasks
  []
  [{:text "Incline pigeon pose for 2 minutes (each side)"
    :estimate 5 :tags ["Medium" "Home" "Fitness"]}
   {:text "Couch (quad) stretch for 2 minutes (each side)"
    :estimate 5 :tags ["Medium" "Home" "Fitness"]}
   {:text "Butterfly pose for 2 minutes"
    :estimate 3 :tags ["Medium" "Home" "Fitness"]}])


(defn generate-lifting-setup-tasks
  "Generate a list of tasks to setup for a lifting workout. Any keys passed
   in will be used to generate 'load the bar' task for that exercise's weight."
  [& {:keys [bench overhead deadlift squat tibialis jefferson]}]
  (filter some?
          [(when bench
             {:text (str "Load barbell for bench press to a total of "
                         (-> bench (max 20) (int) (quot 5) (inc) (* 5))
                         "kgs")
              :tags ["Low" "Home" "Fitness"]
              :estimate 1})
           (when overhead
             {:text (str "Load barbell for overhead press to a total of "
                         (-> overhead (max 20) (int) (quot 5) (inc) (* 5))
                         "kgs")
              :tags ["Low" "Home" "Fitness"]
              :estimate 1})
           (when deadlift
             {:text (str "Load trap bar for deadlift to a total of "
                         (-> deadlift (max 20) (int) (quot 5) (inc) (* 5))
                         "kgs")
              :tags ["Low" "Home" "Fitness"]
              :estimate 1})
           (when squat
             {:text (str "Load barbell for squats to a total of "
                         (-> squat (max 20) (int) (quot 5) (inc) (* 5))
                         "kgs")
              :tags ["Low" "Home" "Fitness"]
              :estimate 1})
           (when tibialis
             {:text (str "Load tib bar to a total of "
                         (int tibialis) "kgs")
              :tags ["Low" "Home" "Fitness"]
              :estimate 1})
           (when jefferson
             {:text (str "Load barbell for jefferson curls to a total of "
                         (-> jefferson (max 0) (int) (quot 5) (inc) (* 5))
                         "kgs")
              :tags ["Low" "Home" "Fitness"]
              :estimate 1})]))


(defn generate-upper-lift-tasks
  "Generate a short list of upper body lifting tasks for a given set of weight
   and repetition definitions.
   Assumes you have a bench, overhead press, & pullup bar accessible."
  [& {:keys [bench overhead pullups]
      :or {bench {:kg 40 :reps 10}
           overhead {:kg 20 :reps 10}
           pullups {:kg 0 :reps 5}}}]
  (let [bench {:kg (-> bench :kg (max 20) (int) (quot 5) (inc) (* 5))
               :reps (max 1 (:reps bench))}
        overhead {:kg (-> overhead :kg (max 20) (int) (quot 5) (inc) (* 5))
                  :reps (max 1 (:reps overhead))}
        pullups {:kg (max 0 (:kg pullups))
                 :reps (max 1 (:reps pullups))}]
    [{:text "Shrug 16kg for 10 reps"
      :tags ["High" "Home" "Fitness"]
      :estimate 3}
     {:text (str "Bench Press "
                 (int (:kg bench)) "kg for "
                 (int (:reps bench)) " reps")
      :tags ["High" "Home" "Fitness"]
      :estimate 3}
     {:text (str "Overhead Press "
                 (int (:kg overhead)) "kg for "
                 (int (:reps overhead)) " reps")
      :tags ["High" "Home" "Fitness"]
      :estimate 3}
     {:text (if (and (integer? (:kg pullups)) (pos? (:kg pullups)))
              (str "Weighted pullups "
                   (int (:kg pullups)) "kg for "
                   (int (:reps pullups)) " reps")
              (str "Bodyweight pullups for "
                   (int (:reps pullups)) " reps"))
      :tags ["High" "Home" "Fitness"]
      :estimate 3}]))


(defn generate-lower-lift-tasks
  "Generate a short list of lower body lifting tasks for a given set of weight
   and repetition definitions.
   Assumes you have a deadlift trap bar, squat rack (+ bar), & tibialis bar.
   Also assumes you have a setup suitable for jefferson curls and calf raises."
  [& {:keys [squat jefferson deadlift tibialis calf]
      :or {squat {:kg 20 :reps 10}
           jefferson {:kg 5 :reps 10}
           deadlift {:kg 40 :reps 10}
           tibialis {:kg 5 :reps 10}
           calf {:kg 5 :reps 10}}}]
  (let [squat {:kg (-> squat :kg (max 20) (int) (quot 5) (inc) (* 5))
               :reps (max 1 (:reps squat))}
        jefferson {:kg (max 0 (:kg jefferson))
                   :reps (max 1 (:reps jefferson))}
        deadlift {:kg (-> deadlift :kg (max 20) (int) (quot 5) (inc) (* 5))
                  :reps (max 1 (:reps deadlift))}
        tibialis {:kg (max 0 (:kg tibialis)) :reps (max 1 (:reps tibialis))}
        calf {:kg (max 0 (:kg calf)) :reps (max 1 (:reps calf))}]
    [{:text (str "Back Squat "
                 (int (:kg squat)) "kg for "
                 (int (:reps squat)) " reps")
      :tags ["High" "Home" "Fitness"]
      :estimate 3}
     {:text (str "Static hang from pullup bar for 30 seconds")
      :tags ["High" "Home" "Fitness"]
      :estimate 1}
     (if (pos? (:kg jefferson))
       {:text (str "Jefferson Curl "
                   (int (:kg jefferson)) "kg for "
                   (int (:reps jefferson)) " reps")
        :tags ["High" "Home" "Fitness"]
        :estimate 3}
       {:text (str "(Unweighted) Jefferson Curl for "
                   (int (:reps jefferson)) " reps")
        :tags ["High" "Home" "Fitness"]
        :estimate 3})
     {:text (str "Deadlift "
                 (int (:kg deadlift)) "kg for "
                 (int (:reps deadlift)) " reps")
      :tags ["High" "Home" "Fitness"]
      :estimate 3}
     (if (pos? (:kg tibialis))
       {:text (str "Tibialis Raises "
                   (int (:kg tibialis)) "kg for "
                   (int (:reps tibialis)) " reps")
        :tags ["High" "Home" "Fitness"]
        :estimate 3}
       {:text (str "(Unweighted) Tibialis Raises for "
                   (int (:reps tibialis)) " reps")
        :tags ["High" "Home" "Fitness"]
        :estimate 3})
     (if (pos? (:kg calf))
       {:text (str "Single Leg Calf Raises "
                   (int (:kg calf)) "kg for "
                   (int (:reps calf)) " reps"
                   " (each leg)")
        :tags ["High" "Home" "Fitness"]
        :estimate 3}
       {:text (str "(Unweighted) Single Leg Calf Raises for "
                   (int (:reps calf)) " reps"
                   " (each leg)")
        :tags ["High" "Home" "Fitness"]
        :estimate 3})]))


(defn generate-recovery-lift-tasks
  "Generate a list of lifting tasks suitable for a 'recovery' day workout.
   (This is usually the day after a long run in a distance training plan.)
   Assumes you have a bench, tibialis bar, pullup bar, & dumbbells accessible."
  [& {:keys [split jefferson tibialis calf]
      :or {split {:kg 5 :reps 10}
           jefferson {:kg 5 :reps 10}
           tibialis {:kg 5 :reps 10}
           calf {:kg 5 :reps 10}}}]
  (let [split {:kg (max 5 (:kg split)) :reps (max 1 (:reps split))}
        jefferson {:kg (max 0 (:kg jefferson)) :reps (max 1 (:reps jefferson))}
        tibialis {:kg (max 0 (:kg tibialis)) :reps (max 1 (:reps tibialis))}
        calf {:kg (max 0 (:kg calf)) :reps (max 1 (:reps calf))}]
    [{:text (str "(ATG) Split Squat with "
                 (int (:kg split)) "kg for "
                 (int (:reps split)) " reps (each side)")
      :tags ["High" "Home" "Fitness"]
      :estimate 3}
     {:text (str "Hanging Leg Raise for 10 reps")
      :tags ["High" "Home" "Fitness"]
      :estimate 3}
     (if (pos? (:kg jefferson))
       {:text (str "Jefferson Curl "
                   (int (:kg jefferson)) "kg for "
                   (int (:reps jefferson)) " reps")
        :tags ["High" "Home" "Fitness"]
        :estimate 3}
       {:text (str "(Unweighted) Jefferson Curl for "
                   (int (:reps jefferson)) " reps")
        :tags ["High" "Home" "Fitness"]
        :estimate 3})
     (if (pos? (:kg tibialis))
       {:text (str "Tibialis Raises "
                   (int (:kg tibialis)) "kg for "
                   (int (:reps tibialis)) " reps")
        :tags ["High" "Home" "Fitness"]
        :estimate 3}
       {:text (str "(Unweighted) Tibialis Raises for "
                   (int (:reps tibialis)) " reps")
        :tags ["High" "Home" "Fitness"]
        :estimate 3})
     (if (pos? (:kg calf))
       {:text (str "Single Leg Calf Raises "
                   (int (:kg calf)) "kg for "
                   (int (:reps calf)) " reps"
                   " (each leg)")
        :tags ["High" "Home" "Fitness"]
        :estimate 3}
       {:text (str "(Unweighted) Single Leg Calf Raises for "
                   (int (:reps calf)) " reps"
                   " (each leg)")
        :tags ["High" "Home" "Fitness"]
        :estimate 3})]))


(defn generate-hiit-workout-tasks
  []
  [{:text "Do a 4x4 HIIT workout" :tags ["High" "Home" "Fitness"]
    :estimate 35}])



(defn generate-end-of-workout-tasks
  []
  [{:text "Have a cold shower" :tags ["Low" "Home" "Mindfulness"]
    :estimate 5}])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;; ACTUAL WORKOUT GENERATION ;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn generate-workout-tasks
  "Generate list of workout tasks, given logs, bodyweight, target, etc."
  [& {:keys [logs bodyweight workout-type distance pace]
      :or {workout-type :run distance 5 pace 6}}]
  (let [targets (strength-targets :logs logs :bodyweight bodyweight)]
    (cond (= workout-type :upper)
          (concat
           (generate-pre-running-tasks) ;; get ready for a run
           (generate-running-tasks :distance 2 :pace 6) ;; quick warmup run
           (generate-pre-lift-stretch-tasks) ;; pre-lift stretching
           (generate-lifting-setup-tasks ;; setup for warmups
            :bench (/ (get-in targets [:bench :kg]) 2)
            :overhead (/ (get-in targets [:overhead :kg]) 2))
           ;; warmup set —
           (generate-upper-lift-tasks
            :bench {:kg (/ (get-in targets [:bench :kg]) 2)
                    :reps (get-in targets [:bench :reps])}
            :overhead {:kg (/ (get-in targets [:overhead :kg]) 2)
                       :reps (get-in targets [:overhead :reps])}
            :pullups {:kg (get-in targets [:pullups :kg])
                      :reps (get-in targets [:pullups :reps])})
           ;; setup for working set —
           (generate-lifting-setup-tasks
            :bench (get-in targets [:bench :kg])
            :overhead (get-in targets [:overhead :kg]))
           ;; working set 1 —
           (generate-upper-lift-tasks
            :bench (:bench targets)
            :overhead (:overhead targets)
            :pullups (:pullups targets))
           ;; working set 2 —
           (generate-upper-lift-tasks
            :bench (:bench targets)
            :overhead (:overhead targets)
            :pullups (:pullups targets))
           ;; working set 3 —
           (generate-upper-lift-tasks
            :bench (:bench targets)
            :overhead (:overhead targets)
            :pullups (:pullups targets))
           ;; post-lift stretching —
           (generate-post-lift-stretch-tasks)
           (generate-end-of-workout-tasks))

          (= workout-type :lower)
          (concat
           (generate-pre-running-tasks) ;; get ready for a run
           (generate-running-tasks :distance 2 :pace 6) ;; quick warmup run
           (generate-pre-lift-stretch-tasks) ;; pre-lift stretching
           ;; setup for warmups —
           (generate-lifting-setup-tasks
            :squat (/ (get-in targets [:squat :kg]) 2)
            :deadlift (/ (get-in targets [:deadlift :kg]) 2)
            :tibialis (/ (get-in targets [:tibialis :kg]) 2)
            :calf (/ (get-in targets [:calf :kg]) 2)
            :jefferson (/ (get-in targets [:jefferson :kg]) 2))
           ;; warmup set —
           (generate-lower-lift-tasks
            :squat {:kg (/ (get-in targets [:squat :kg]) 2)
                    :reps (get-in targets [:squat :reps])}
            :deadlift {:kg (/ (get-in targets [:deadlift :kg]) 2)
                       :reps (get-in targets [:deadlift :reps])}
            :tibialis {:kg (/ (get-in targets [:tibialis :kg]) 2)
                       :reps (get-in targets [:tibialis :reps])}
            :calf {:kg (/ (get-in targets [:calf :kg]) 2)
                   :reps (get-in targets [:calf :reps])}
            :jefferson {:kg (/ (get-in targets [:jefferson :kg]) 2)
                        :reps (get-in targets [:jefferson :reps])})
           ;; setup for working set —
           (generate-lifting-setup-tasks
            :squat (get-in targets [:squat :kg])
            :deadlift (get-in targets [:deadlift :kg])
            :tibialis (get-in targets [:tibialis :kg])
            :calf (get-in targets [:calf :kg])
            :jefferson (get-in targets [:jefferson :kg]))
           ;; working set 1 —
           (generate-lower-lift-tasks
            :squat (:squat targets)
            :deadlift (:deadlift targets)
            :tibialis (:tibialis targets)
            :calf (:calf targets)
            :jefferson (:jefferson targets))
           ;; working set 2 —
           (generate-lower-lift-tasks
            :squat (:squat targets)
            :deadlift (:deadlift targets)
            :tibialis (:tibialis targets)
            :calf (:calf targets)
            :jefferson (:jefferson targets))
           ;; post-lift stretching —
           (generate-post-lift-stretch-tasks)
           (generate-end-of-workout-tasks))

          (= workout-type :recovery)
          (concat (generate-pre-lift-stretch-tasks) ;; pre-lift stretching
                  ;; setup for workout —
                  (generate-lifting-setup-tasks
                   :split (get-in targets [:split :kg])
                   :bench (get-in targets [:bench :kg])
                   :jefferson (get-in targets [:jefferson :kg])
                   :tibialis (get-in targets [:tibialis :kg])
                   :calf (get-in targets [:calf :kg]))
                  ;; workout — 
                  (generate-recovery-lift-tasks)
                  (generate-post-lift-stretch-tasks)
                  (generate-end-of-workout-tasks))

          (= workout-type :hiit)
          (concat (generate-pre-running-tasks)
                  (generate-hiit-workout-tasks)
                  (generate-post-cardio-tasks))

          (= workout-type :long-run)
          (concat (generate-pre-running-tasks)
                  (generate-running-tasks :distance (-> distance
                                                        (* 1.5)
                                                        (min 40)
                                                        (max 4)
                                                        (int))
                                          :pace pace)
                  (generate-post-cardio-tasks))

          (= workout-type :short-run)
          (concat (generate-pre-running-tasks)
                  (generate-running-tasks :distance (-> distance
                                                        (* 0.7)
                                                        (min 10)
                                                        (max 3)
                                                        (int))
                                          :pace pace)
                  (generate-post-cardio-tasks))
          (= workout-type :run)
          (concat (generate-pre-running-tasks)
                  (generate-running-tasks :distance (-> distance
                                                        (min 15)
                                                        (max 3)
                                                        (int))
                                          :pace pace)
                  (generate-post-cardio-tasks))

          :else ;; randomly choose otherwise
          (generate-workout-tasks
           :logs logs
           :bodyweight bodyweight
           :workout-type (rand-nth [:upper :lower :recovery :hiit
                                    :long-run :short-run :run])
           :distance distance
           :pace pace))))


(defn tasks->taskpaper
  "Given a list of workout tasks (and optional date for due/defer), convert to
   a taskpaper string."
  [& {:keys [tasks date title]
      :or {date (today)
           title "Workout"}}]
  (let [total-time (+ 5 (->> tasks (map :estimate) (filter some?) (apply +)))
        header (str "- " title
                    " @due(" date " 7pm)"
                    " @defer(" date " 4am)"
                    " @estimate(" total-time "m)"
                    " @autodone(true)"
                    " @parallel(false)"
                    "\n")]
    (str header (str/join "" (map #(str "\t" %) (map build-task tasks))))))


(defn generate-workout
  "Generate a workout as a taskpaper string given logs, bodyweight, target etc"
  [& {:keys [logs bodyweight workout-type distance pace date]
      :or {workout-type :run distance 5 pace 6 date (today)}}]
  (let [tasks (generate-workout-tasks :logs logs
                                      :bodyweight bodyweight
                                      :workout-type workout-type
                                      :distance distance
                                      :pace pace)
        title (cond (= workout-type :upper) "Complete an upper body workout"
                    (= workout-type :lower) "Complete a lower body workout"
                    (= workout-type :recovery) "Complete a recovery workout"
                    (= workout-type :long-run) "Go for a long run"
                    (= workout-type :short-run) "Go for a short run"
                    (= workout-type :run) "Go for a run"
                    :else "Complete a Workout")]
    (tasks->taskpaper :tasks tasks :date date :title title)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CLI ARGUMENTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def cli-spec ;; CLI argument spec.
  {:lifts {:default (->> ["~"
                          "Library"
                          "Mobile Documents"
                          "iCloud~is~workflow~my~workflows"
                          "Documents"
                          "Logs"
                          "Fitness"
                          "Lifting"]
                         (str/join "/")
                         (fs/expand-home)
                         (fs/unixify))
           :help "The directory containing json logs of lifts to process."
           :parse-fn str}
   :runs {:default "~/Documents/blog/public/logs/running.csv"
          :help "The path to a CSV log of runs."
          :parse-fn str}
   :bodyweight {:default 85
                :help "Your current bodyweight in kg."
                :parse-fn int}
   :gender {:default nil
            :help "Your gender (opts: male or female)"
            :parse-fn str}
   :n      {:default 1
            :help "Number of workouts to generate."
            :parse-fn int}
   :equipment {:default nil
               :help "The equipment you have available (opts: home, gym, none)"
               :parse-fn str}
   :help {:coerce :boolean}})


(def cli-aliases ;; CLI argument aliases for convenience.
  {:l :lifts})


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
        lifts (-> opts
                  :lifts
                  (fs/expand-home)
                  (fs/unixify)
                  list-json-files
                  read-lifting-logs)
        runs (-> opts
                 :runs
                 (fs/expand-home)
                 (fs/unixify)
                 read-run-log)
        n (:n opts)
        bodyweight (:bodyweight opts)]
    (doseq [i (range n)]
      (let [date (last (days (inc i)))
            workout (generate-workout :logs lifts
                                      :bodyweight bodyweight
                                      :workout-type (which-workout? date)
                                      :date date
                                      :distance (if (empty? runs) 5
                                                    (recent-kms runs)))]
        (println workout)))))


(-main)