#!/usr/bin/env bb
; 
; 

(require '[clojure.java.shell :refer [sh]])


; TODOIST INFO
(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map keyword) ;; Drop if you want string keys instead
            repeat)
	  (rest csv-data)))

(defn get-sorted-tasks [todoist-results]
  (sort-by :Priority (into [] (-> todoist-results
                                  (:out)
                                  (csv/read-csv)
                                  (csv-data->maps)))))

(def overdue (get-sorted-tasks (sh "/usr/local/bin/todoist"
                                   "--csv" "--header"
                                   "list" "--filter" "(overdue)")))

(def today (get-sorted-tasks (sh "/usr/local/bin/todoist"
                                   "--csv" "--header"
                                   "list" "--filter" "(today)")))

(def other-tasks (get-sorted-tasks (sh "/usr/local/bin/todoist"
                                       "--csv" "--header" "list")))

(def has-due (remove #(str/blank? (:Date %)) other-tasks))

(def best-task (first (remove nil? (conj [] (first overdue)
                                            (first today)
                                            (first other-tasks)
                                            (first has-due)))))

(def completed (sort-by :CompletedDate (into [] (-> (sh "/usr/local/bin/todoist"
                                                        "--csv" "--header"
                                                        "completed-list")
                                                    (:out)
                                                    (csv/read-csv)
                                                    (csv-data->maps)))))

(def completed-today (sort-by :CompletedDate (into [] (-> (sh "/usr/local/bin/todoist"
                                                        "--csv" "--header"
                                                        "completed-list"
                                                        "--filter" "(today)")
                                                    (:out)
                                                    (csv/read-csv)
                                                    (csv-data->maps)))))

; TASK ADDING
(defn add-task "Send a system call to add a task to todoist"
  [task project-name date priority]
  (sh "/usr/local/bin/todoist" "add"
      "--priority" (str priority)
      "--date" date
      "--project-name" project-name
      task))

; (add-task "Do 4 pushups" "Exercise" "today 1pm" 1)
; (println (last completed)) ; most recent


(defn mean [coll]
  (let [sum (apply + coll)
        count (count coll)]
    (if (pos? count)
      (/ sum count)
      0)))

; DECISION SUPPORT FOR TASKS

(defn hour [] (Integer/parseInt (str/trim (:out (sh "date" "+%H")))))

(def do-pushups?
  (let [current-hour (hour)]
    (and (contains? (into [] (range 5 20)) current-hour)
         (> 1 (count (filter #(re-find #"pushups" (:Content %))
                             today)))
         (> 1 (count (filter #(re-find #"pushups" (:Content %))
                             overdue)))
         (or (> 2 (count (filter #(re-find #"pushups" (:Content %))
                          (subvec (into [] (reverse completed)) 0 10))))
             (> 5 (count (filter #(re-find #"pushups" (:Content %))
                                 completed-today)))))))

(def do-pullups?
  (let [current-hour (hour)]
    (and (or (contains? (into [] (range 5 9)) current-hour)
             (contains? (into [] (range 17 22)) current-hour))
         (> 1 (count (filter #(re-find #"pullups" (:Content %))
                             today)))
         (> 1 (count (filter #(re-find #"pullups" (:Content %))
                             overdue)))
         (or (> 2 (count (filter #(re-find #"pullups" (:Content %))
                          (subvec (into [] (reverse completed)) 0 10))))
             (> 5 (count (filter #(re-find #"pullups" (:Content %))
                                 completed-today)))))))

(def do-anki?
  (and (> 1 (count (filter #(= "Anki" (:Project %)) completed-today)))
       (> 1 (count (filter #(= "Anki" (:Project %)) today)))
       (> 1 (count (filter #(= "Anki" (:Project %)) overdue)))))

(def average-pushups
  (->> completed
       (filter #(re-find #"pushups" (:Content %)))
       (map #(re-find #"\d+" (:Content %)))
       (concat)
       (remove nil?)
       (map #(Integer/parseInt %))
       (mean)
       (float)
       (Math/round)))

(def average-pullups
  (->> completed
       (filter #(re-find #"pullups" (:Content %)))
       (map #(re-find #"\d+" (:Content %)))
       (concat)
       (remove nil?)
       (map #(Integer/parseInt %))
       (mean)
       (float)
       (Math/round)
       (int)
       ))

(def best-pullups
  (rand-nth (list (max 2 (min 20 (+ 1 average-pullups)))
                  (+ 1 (rand-int 6))
                  2)))

(def best-pushups
  (rand-nth (list (* 5 (Math/round (float (/ average-pushups 5))))
                  (min 100 (* 5 (Math/round (float (/ (+ 2.5 average-pushups) 5)))))
                  10)))

(def do-journal?
  (let [current-hour (hour)]
    (and (< current-hour 9)
         (> 1 (count (filter #(= "Blog" (:Project %)) today)))
         (> 1 (count (filter #(= "Blog" (:Project %)) completed-today))))))

(def do-forecast?
  (let [current-hour (hour)]
    (and (> current-hour 17)
         (> 1 (count (filter #(= "Predict" (:Project %)) today)))
         (> 1 (count (filter #(= "Predict" (:Project %)) completed-today))))))


; [task project-name date priority]
; MAIN
(cond (true? do-journal?) (add-task "Write morning pages"
                                    "Blog" "today" 1)
      (true? do-pullups?) (add-task (str/join " " ["Do" (str best-pullups) "pullups"])
                                    "Exercise" "today" 2)
      (true? do-pushups?) (add-task (str/join " " ["Do" (str best-pushups) "pushups"])
                                    "Exercise" "today" 2)
      (true? do-anki?) (add-task "Complete all anki decks"
                                 "Anki" "today" 3))

