#!/usr/bin/env bb
;;
;; Take whatever's on the clipboard and try to create a well-yaml-headered note
;; from it. If a new note gets created, we print the absolute path to it; if one
;; already exists that matches our best attempt, we just print that path.
;;
;; Currently supports:
;; - doi -> 'review'
;; - isbn -> 'review'
;; - metaculus question link -> 'forecast'
;;
;;
;; TODO:
;; - link -> 'review' with title generation
;; - passage -> 'quote' with viable/stable permalink & title generation
;; - note
;; - citation questions

(require '[clojure.java.shell :refer [sh]]
         '[clojure.java.io :as io]
         '[babashka.curl :as curl])

(def base-notes-directory "/home/g/Documents/blog/_posts/")
(def input-lines (line-seq (io/reader *in*)))


(defn remove-nils
  "Remove nils from a map.
  https://stackoverflow.com/questions/3937661/remove-nil-values-from-a-map"
  [m]
  (let [f (fn [x]
            (if (map? x)
              (let [kvs (filter (comp not nil? second) x)]
                (if (empty? kvs) nil (into {} kvs)))
              x))]
    (clojure.walk/postwalk f m)))


(defn titlecase
  "Titlecase some string in a rough-and-ready sort of way."
  [s]
  (let [exclude ["And" "As" "But" "For" "If" "Nor" "Or" "So" "Yet" "A" "An" "The" "At" "By" "In" "Of" "Off" "On" "Per" "To" "Up" "Via"]]
  (->> (map str/capitalize (str/split s #" "))
        (map (fn [word] (if-not (nil? (some #{word} exclude))
                          (str/lower-case word)
                          word)))

        (str/join " "))))

(defn get-datetime-now
  "Get a formatted datetime string for the current time."
  []
  (.format (java.time.ZonedDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "YYYY-MM-dd HH:mm:ss Z")))

(defn get-ymd-now
  "Get the current year-month-day as a formatted string."
  []
  (.format (java.time.ZonedDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "YYYY-MM-dd")))

(defn get-hms-now
  "Get the current hour-minute-second as a formatted string."
  []
  (.format (java.time.ZonedDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "HH-mm-ss")))

(defn grab-dois
  "Extract the doi-ish substrings from a string (in the ugliest regex ever)."
  [s]
  (distinct
   (remove nil?
           (re-find #"(10\.\d{4,9}/[-._;()/:A-Za-z0-9]+|10.1002/[^\s]+|10.\d{4}/\d+-\d+X?(\d+)\d+<[\d\w]+:[\d\w]*>\d+.\d+.\w+;\d|10.1021/\w\w\d+|10.1207/[\w\d]+\&\d+_\d+)" s))))

(defn has-doi?
  "Check whether a given string s seems like it contains a doi." [s]
  (some? (seq (grab-dois s))))

(defn grab-isbns
  "Extract the isbn-ish substrings from a string."
  [s]
  (distinct (remove nil?
                    (re-find #"((?<=^|isbn\/)(?=(?:\D*\d){10}(?:(?:\D*\d){3})?$)[\d-]+$)" s))))

(defn has-isbn?
  "Check whether a given string s seems like it contains an isbn."
  [s]
  (some? (seq (grab-isbns s))))

(defn grab-links
  "Extract the link-ish substrings" [s]
  (distinct
   (remove nil?
           (re-find #"(http|https)://([\w_-]+(?:(?:\.[\w_-]+)+))([\w.,@?^=%&:/~+#-]*[\w@?^=%&/~+#-])?" s))))

(defn is-link?
  "Check whether a string seems like a link" [s]
  (some? (seq (grab-links s))))

(defn is-metaculus-question?
  "Check whether a string seems like a link to a metaculus question" [s]
  (and (is-link? s) (some? (re-find #"metaculus\.com/questions/" s))))

(defn looks-like-a-quote? "Check whether a string looks like a quote TODO" [s]
  (and (> (count s) 100) ;; more than a 100 total characters
       (> (count (str/replace s #"\S" "")) 5))) ;; and more than 5 spaces


(defn construct-query-url "Construct a citoid query url" [id format-type]
  (str/join "/"
            ["https://en.wikipedia.org/api/rest_v1/data/citation"
             (str/trim format-type)
             (str/replace (str/trim id) #"/" "%2F")]))

(defn query-for-cite "Run a query for a given ID" [id format-type]
  (curl/get (construct-query-url id format-type) {:throw false}))

(defn query-and-clean-cite-data "Run and process a query to its body; nil if fail"
  ([id] (query-and-clean-cite-data id "zotero"))
  ([id format-type] (let [response (query-for-cite id format-type)]
                      (if (= (:status response) 200)
                        (:body response)))))

(defn get-clean-title-from-cite-info
  "Assuming a parsed zotero-formatted citation info map, returns best short title as a string (or nil if nothing available) TODO"
  ([citation-info markdownify?]
  (if (contains? citation-info :shortTitle)
    (:shortTitle citation-info)
    (get citation-info :title nil)))
  ([citation-info]
   (get-clean-title-from-cite-info citation-info false)))

(defn shorten-title "TODO" [s]
  (if (nil? s)
    s
    (str/join [(first (str/split s #":")) (re-find #"\**$" s)])))

(defn get-clean-long-author-from-cite-info
  "Assuming a parsed zotero-formatted citation info map, returns best author as a clean string (or nil if nothing available). Can be 'true' or 'false' to a markdownify flag."
  ([citation-info markdownify?]
  (if (contains? citation-info :creators)
    (let [authors (->> (:creators citation-info)
                       (filter #(= "author" (get % :creatorType)))
                       (filter #(not (nil? (get % :lastName)))))
          first-author (first authors)
          second-author (second authors)]
      (cond (> (count authors) 2)
            (str/join " " (into [] (remove nil? [(:firstName first-author)
                                                 (str/join [(if markdownify? "**" "") (:lastName first-author)])
                                                 (str/join ["et al." (if markdownify? "**" "")])])))
            (= (count authors) 2)
            (str/join " " (into [] (remove nil? [(:firstName first-author)
                                                 (str/join [(if markdownify? "**" "") (:lastName first-author) (if markdownify? "**" "")])
                                                 "and"
                                                 (:firstName second-author)
                                                 (str/join [(if markdownify? "**" "") (:lastName second-author) (if markdownify? "**" "")])])))
            (= (count authors) 1)
            (str/join " " (into [] (remove nil? [(:firstName first-author)
                                                 (str/join [(if markdownify? "**" "") (:lastName first-author)])])))
              :else nil))))
  ([citation-info]
   (get-clean-long-author-from-cite-info citation-info false)))

(defn get-clean-short-author-from-cite-info
  "Assuming a parsed zotero-formatted citation info map, returns *short* author (usually just last names) as a clean string (or nil if nothing available). Can be 'true' or 'false' to a markdownify flag."
  ([citation-info markdownify?]
    (let [no-firsts (update-in citation-info [:creators]
                              (fn [creators] (map #(dissoc % :firstName) creators)))]
      (get-clean-long-author-from-cite-info no-firsts markdownify?)))
  ([citation-info]
    (get-clean-short-author-from-cite-info citation-info false)))

(defn get-best-post-category
  "Take a lazy seq of input lines and return a best-guess post category." [input-lines]
  (let [lines (str/join input-lines)]
    (cond
      (is-metaculus-question? (str/trim lines)) "forecast"
      (looks-like-a-quote? lines) "quote"
      (or (has-doi? (str/trim lines)) (is-link? (str/trim lines)) (has-isbn? (str/trim lines))) "review"
      :else "note")))

(defn add-reading-dates-and-rating-fields [post-header]
  (if (= (:category post-header) "review")
    (-> post-header
        (assoc :reading_started_date (get-datetime-now))
        (assoc :reading_completed_date (get-datetime-now)))
    post-header))

(defn add-raw-citation-ids "TODO" [post-header input-lines]
  (let [lines (str/join input-lines)
        best-doi (first (grab-dois lines))
        best-isbn (first (grab-isbns lines))]
    (-> post-header
        (assoc :doi best-doi)
        (assoc :isbn best-isbn)
        (assoc :reading_link (if (and (is-link? lines) (not (is-metaculus-question? lines)))
                                      (first (grab-links lines)) nil)))))

(defn add-resolved-citation-info "TODO" [post-header]
  (if-let [clean-id (first (remove nil? [(:doi post-header) (:isbn post-header)]))]
    (let [citation-info (first (json/parse-string (query-and-clean-cite-data clean-id "zotero") true))]
      (-> post-header
          (assoc :reading_title (get-clean-title-from-cite-info citation-info))
          (assoc :reading_author (get-clean-long-author-from-cite-info citation-info))))
    post-header))


(defn query-metaculus-question "Run a query for a metaculus question ID" [id]
  (let [response (curl/get (str/join ["https://www.metaculus.com/api2/questions/" id "/"]) {:throw false})]
    (if (= (:status response) 200)
      (json/parse-string (:body response) true))))


(defn add-metaculus-metadata "TODO" [post-header input-lines]
  (if (and (= (:category post-header) "forecast")
           (is-metaculus-question? (str/join input-lines)))
    (let [question-id (re-find #"(?<=questions\/)\d+(?=\/)" (str/join input-lines))
          metadata (query-metaculus-question question-id)]
      (-> post-header
          (assoc :metaculus_question_metadata
                 (select-keys metadata [:id :title :created_time :publish_time :close_time :resolve_time :title_short]))))
    post-header))

(defn permalinkify "TODO" [s]
  (-> s
      (str/lower-case)
      (str/replace #"“|”" "\"")
      (str/replace #"“|”" "\"")
      (str/replace #"‘|’" "'")
      (str/trim)
      (str/replace #"[^a-zA-Z0-9 _-]" "")
      (str/replace #" +" " ")
      (str/replace #" " "-")))

(defn build-review-permalink "TODO" [post-header]
  (if-let [clean-id (first (remove nil? [(:doi post-header) (:isbn post-header)]))]
    (let [citation-info (first (json/parse-string (query-and-clean-cite-data clean-id "zotero") true))]
      (-> (str/join [(shorten-title (get-clean-title-from-cite-info citation-info))
                     " "
                     (get-clean-short-author-from-cite-info citation-info)])
          (str/replace #" and " " ")
          (permalinkify)))
    ""))

(defn add-backup-permalink "TODO" [post-header]
  (if (str/ends-with? (:permalink post-header) "/")
    (update-in post-header [:permalink] #(str/join [% (get-ymd-now) "-" (get-hms-now)]))
    post-header))

(defn add-best-permalink "TODO" [post-header]
  (let [prefix (cond (= (:category post-header) "forecast") "/f/"
                     (= (:category post-header) "quote") "/q/"
                     (= (:category post-header) "review") "/r/"
                     (= (:category post-header) "note") "/n/"
                     :else "/")]
    (-> (cond (and (= (:category post-header) "forecast")
                   (get-in post-header [:metaculus_question_metadata :id]))
              (assoc post-header :permalink (str/join [prefix (get-in post-header [:metaculus_question_metadata :id])]))
              (= (:category post-header) "review") (assoc post-header :permalink (str/join [prefix (build-review-permalink post-header)]))
              ;; (= (:category post-header) "quote") ()
              ;; (= (:category post-header) "note") ()
            :else (assoc post-header :permalink (str/join [prefix (get-ymd-now) "-" (get-hms-now)])))
        (add-backup-permalink))))

(defn construct-post-title-for-review
  "Use whatever existing reading_author and reading_title is available in a post-header to generate a post title TODO"
  [post-header]
  (let [author (:reading_author post-header)
        title (:reading_title post-header)]
    (cond (and (nil? author) (some? title)) (str/trim title)
          (and (some? author) (nil? title)) (str/join ["Some book by " (str/trim author)])
          (and (nil? author) (nil? title)) nil
          :else (str/join [(str/trim author) "'s " "'" (str/trim title) "'"]))))

(defn add-backup-title "TODO" [post-header]
  post-header)

(defn add-best-title "TODO" [post-header]
  (-> (cond (= (:category post-header) "forecast") (assoc post-header :title (get-in post-header [:metaculus_question_metadata :title]))
            (= (:category post-header) "review") (assoc post-header :title (construct-post-title-for-review post-header))
            ;; (= (:category post-header) "quote") (assoc post-header :title nil)
            ;; (= (:category post-header) "note") (assoc post-header :title nil)
            :else (assoc post-header :title nil))
      (add-backup-title)))


(defn add-raw-authorship-question
  "Append a raw question/answer pair about authorship"
  [questions cite-map]
  )

(defn build-question-map "TODO" [question answer]
  {:question {:question question
              :answer answer}})

(defn citoid->questions
  "Take the (proccessed/map) returns from a citoid query and translate into a list of basic questions. TODO"
  [cite-map]
  (let [long-author (get-clean-long-author-from-cite-info cite-map true)
        short-author (get-clean-short-author-from-cite-info cite-map true)
        title (shorten-title (get-clean-title-from-cite-info cite-map))]
    ;; ((into [] (filter nil? [(build-authorship-question )])
    ;; (-> []
    ;;     (add-raw-authorship-question cite-map)
    ;;     (add-date-published-question cite-map)
    ;;     (add-journal-publisher-question cite-map)
    ;;     (add-pages)
    ;; )
    (map (fn [x] (build-question-map (:question x) (:answer x))) [{:question "my test question?" :answer "a test answer"}])
    ;; [{:question {:long-author long-author
    ;;              :short-author short-author
    ;;              :long-title long-title
    ;;              :short-title short-title}}]
    ))

(defn add-bibliographic-questions "TODO" [post-header]
  (if-let [clean-id (first (remove nil? [(:doi post-header) (:isbn post-header)]))]
    (let [citation-info (first (json/parse-string (query-and-clean-cite-data clean-id "zotero") true))]
      (-> post-header
          (assoc :citation_info citation-info)
          (assoc :questions (citoid->questions citation-info))))
    post-header))

(defn construct-post-header "Construct a post header from command line input lines" [input-lines]
  (-> {:layout "post"
       :date (get-datetime-now)
       :published false
       :category (get-best-post-category input-lines)
       :body (if (= (get-best-post-category input-lines) "quote")
               (dorun (map #(str/join "\n" ["> " (str/trim %)]) input-lines))
               "")}
      (add-reading-dates-and-rating-fields)
      (add-raw-citation-ids input-lines)
      (add-resolved-citation-info)
      (add-metaculus-metadata input-lines)
      (add-best-permalink)
      (add-best-title)
      (add-bibliographic-questions)
      (remove-nils)))

(defn extract-short-permalink "Take a permalink string and remove its prefix (and also datetime if that's what it appears to be)" [s]
  (-> s
      (str/split #"\/")
      (last)
      (str/replace #"\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}" "")
      (str/replace #"\d{4}-\d{2}-\d{2}" "")))

(defn generate-outpath "TODO" [base-path post-header]
  (let [folder (cond (= (:category post-header) "forecast") "forecasts"
                     (= (:category post-header) "quote") "quotes"
                     (= (:category post-header) "review") "readings"
                     (= (:category post-header) "note") "notes"
                     (= (:category post-header) "journal") "journals"
                     :else "notes")
        short-perma (last (str/split (get post-header :permalink (get-hms-now)) #"\/"))
        rough-filename (str/join [(get-ymd-now) "-" short-perma ".md"])
        filename (str/replace rough-filename (str/join [(get-ymd-now) "-" (get-ymd-now)]) (get-ymd-now))]
    (str/join [base-path folder "/" filename])))

(defn build-yaml-header-string
  "Take a post-header and construct a valid string for it"
  [post-header]
  (let [categories (:category post-header)
        clean-header (dissoc post-header :category :body)]
    (str/join ["---" "\n"
              (yaml/generate-string clean-header
                                    :dumper-options {:flow-style :block})
              (str/join ["categories: ['" categories "']\n"])
              "---" "\n" "\n"])))

(defn build-full-file-string
  "Take a post header and construct a complete file string"
  [post-header]
  (let [clean-body (:body post-header)]
    (str/join [(build-yaml-header-string post-header)
              clean-body])))

(defn list-existing-matching-files
  "TODO"
  [base-path post-header]
  (if (some? (:permalink post-header))
    (let [permalink-to-match (extract-short-permalink (:permalink post-header))]
      (if-not (str/blank? permalink-to-match)
        (into [] (filter #(some? (re-find (re-pattern permalink-to-match) %))
                         (map #(.getPath %) (file-seq (clojure.java.io/file base-path)))))
        []))
    []))

(defn safe-create-file-with-header "TODO" [base-path post-header]
  (let [outpath (generate-outpath base-path post-header)
        header-string (build-yaml-header-string post-header)
        existing-paths (list-existing-matching-files base-path post-header)]
    (cond (not= 0 (count existing-paths)) ; if a matching path already exists, just print the first
            (println (first existing-paths))
          (and (= 0 (count existing-paths)) (some? (:title post-header))) ; if no path, and a valid title exists, print the header and outpath
            (do (spit outpath header-string) (println outpath))
          :else (println ""))))



;; MAIN - ACTUALLY RUN THIS BASTARD
(defn -main "TODO" [input-lines notes-directory]
  (->> input-lines
       (construct-post-header)
       (safe-create-file-with-header base-notes-directory)))
;; (-main input-lines base-notes-directory)


(println (titlecase "and so I went down to the ship, set keel to breakers"))

(defn test-display-proposed-header "TODO" [input-lines notes-directory]
  (->> input-lines
       (construct-post-header)
       (build-yaml-header-string)
       println))
(test-display-proposed-header input-lines base-notes-directory)



;; (let [post-header (construct-post-header input-lines)]
;;   (if-let [clean-id (first (remove nil? [(:doi post-header) (:isbn post-header)]))
;;            citation-info (first (json/parse-string (query-and-clean-cite-data clean-id "zotero") true))]
;;     (println citation-info)
;;     ""))

