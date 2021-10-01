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

(require '[clojure.java.shell :refer [sh]]
         '[clojure.java.io :as io]
         '[babashka.curl :as curl]
         '[clojure.data.xml :as xml])

(defn expand-home [s]
  (if (.startsWith s "~")
    (clojure.string/replace-first s "~" (System/getProperty "user.home"))
    s))

(def base-notes-directory (expand-home "~/Documents/blog/_posts/"))
(def input-lines (line-seq (io/reader *in*)))
(def excludeable-words ["and" "as" "but" "for" "if" "nor" "or" "so" "yet" "a" "an" "the" "at" "by" "in" "of" "off" "on" "per" "to" "up" "via" "is"])

(defn get-current-location "TODO" []
  (let [response (curl/get "https://ipinfo.io" {:throw false})]
    (if (= (:status response) 200)
      (:loc (json/parse-string (:body response) true))
      nil)))

(defn get-title-from-link "TODO" [link]
  (let [response (curl/get link {:throw false})]
    (if (= (:status response) 200)
      (let [titles (re-find #"(?<=\<title).+(?=\<\/title)" (:body response))]
        (cond (string? titles) (str/replace titles #"^.*>" "")
              (vector? titles) (str/replace (first titles) #"^.*>" "")
              :else titles))
      nil)))

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

(defn contextify
  "For a given vec, return a sequence of maps with {:before [$some_things$] :target $a_thing$ :after [$some_things$]} TODO"
  ([v max]
   (into []
         (map (fn [i] {:before (into [] (reverse (take max (reverse (subvec v 0 i)))))
                          :target (nth v i)
                          :after (into [] (take max (subvec v (+ i 1))))})
                   (range (count v)))))
  ([v]
   (contextify v (count v))))

(defn titlecase
  "Titlecase some string in a rough-and-ready sort of way."
  [s]
  (let [exclude (into [] (map str/capitalize excludeable-words))
        uppercase-first (fn [to-title] (str/join [(str/upper-case (subs to-title 0 1)) (subs to-title 1)]))]
    (->> (map str/capitalize (str/split s #" "))
         (map (fn [word] (if-not (nil? (some #{word} exclude))
                           (str/lower-case word)
                           word)))
         (str/join " ")
         (uppercase-first))))

(defn blackout
  "Blackout a string, optionally skipping punctuation"
  ([s skip-punct?]
   (if skip-punct?
     (str/replace s #"[A-Za-z0-9']" "█")
     (str/replace s #"\S" "█")))
  ([s]
   (blackout s true)))

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

(defn looks-like-a-note? "Check whether a string looks like a note TODO" [s]
  (and (not (is-link? s))
       (not (has-doi? s))
       (not (has-isbn? s))
       (> (count (str/replace s #"\S" "")) 2) ;; at least 3 words (more than 2 spaces)
       (< (count (str/replace s #"\S" "")) 10))) ;; and less than 10 spaces

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
                                                 (str/join [(if markdownify? "**" "") (:lastName first-author) (if markdownify? "**" "")
                                                            ])])))
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
      (looks-like-a-note? lines) "note"
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

(defn build-review-permalink "TODO" [post-header input-lines]
  (if-let [clean-id (first (remove nil? [(:doi post-header) (:isbn post-header)]))]
    (let [citation-info (first (json/parse-string (query-and-clean-cite-data clean-id "zotero") true))]
      (-> (str/join [(shorten-title (get-clean-title-from-cite-info citation-info))
                     " "
                     (get-clean-short-author-from-cite-info citation-info)])
          (str/replace #" and " " ")
          (permalinkify)))
    (if (is-link? (str/join input-lines))
      (let [page-title (get-title-from-link (str/join input-lines))]
        (if (some? page-title) (permalinkify page-title) nil))
      "")))

(defn build-note-permalink "TODO" [input-lines]
  (-> input-lines
      str/join
      str/trim
      permalinkify))

(defn extract-short-permalink "Take a permalink string and remove its prefix (and also datetime if that's what it appears to be)" [s]
  (-> s
      (str/split #"\/")
      (last)
      (str/replace #"\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}" "")
      (str/replace #"\d{4}-\d{2}-\d{2}" "")))

(defn construct-post-title-for-quote
  "TODO"
  ([input-lines max]
  (titlecase
   (str/join " "
             (into []
                   (reverse
                    (drop-while #(not (nil? (some #{%} excludeable-words)))
                                (reverse
                                 (take max (-> input-lines
                                               str/join
                                               str/trim
                                               (str/split #"\s"))))))))))
  ([input-lines]
   (construct-post-title-for-quote input-lines 5)))


(defn build-quote-permalink "TODO" [input-lines]
  (-> input-lines
      (construct-post-title-for-quote 10)
      (permalinkify)))

(defn add-backup-permalink "TODO" [post-header]
  (if (str/ends-with? (:permalink post-header) "/")
    (update-in post-header [:permalink] #(str/join [% (get-ymd-now) "-" (get-hms-now)]))
    post-header))

(defn add-best-permalink "TODO" [post-header input-lines]
  (let [prefix (cond (= (:category post-header) "forecast") "/f/"
                     (= (:category post-header) "quote") "/q/"
                     (= (:category post-header) "review") "/r/"
                     (= (:category post-header) "note") "/n/"
                     :else "/")]
    (-> (cond (and (= (:category post-header) "forecast")
                   (get-in post-header [:metaculus_question_metadata :id]))
              (assoc post-header :permalink (str/join [prefix (get-in post-header [:metaculus_question_metadata :id])]))
              (= (:category post-header) "review") (assoc post-header :permalink (str/join [prefix (build-review-permalink post-header input-lines)]))
              (= (:category post-header) "quote") (assoc post-header :permalink (str/join [prefix (build-quote-permalink input-lines)]))
              (= (:category post-header) "note") (assoc post-header :permalink (str/join [prefix (build-note-permalink input-lines)]))
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

(defn construct-post-title-for-note
  "TODO"
  [input-lines]
  (-> input-lines
      str/join
      str/trim
      titlecase))


(defn add-backup-title "TODO" [post-header input-lines]
  (if (and (nil? (:title post-header)) (is-link? (str/join input-lines))
           (> (count (extract-short-permalink (get post-header :permalink ""))) 2))
    (assoc post-header :title (titlecase (str/replace (last (str/split (:permalink post-header) #"\/")) "-" " ")))
    post-header))

(defn add-best-title "TODO" [post-header input-lines]
  (-> (cond (= (:category post-header) "forecast") (assoc post-header :title (get-in post-header [:metaculus_question_metadata :title]))
            (= (:category post-header) "review") (assoc post-header :title (construct-post-title-for-review post-header))
            (= (:category post-header) "quote") (assoc post-header :title (construct-post-title-for-quote input-lines))
            (= (:category post-header) "note") (assoc post-header :title (construct-post-title-for-note input-lines))
            :else (assoc post-header :title nil))
      (add-backup-title input-lines)))


(defn add-raw-authorship-question
  "Append a raw question/answer pair about authorship TODO"
  ([questions cite-map markdownify?]
   (if (> (count (into [] (filter #(= "author" (:creatorType %)) (:creators cite-map)))) 0)
    (let [title-surround (cond (= "book" (:itemType cite-map)) "***"
                               :else "'")
          date (re-find #"\d{4}" (:date cite-map))]
      (conj questions {:question (str/join [(if markdownify? "*Who*" "Who") " "
                                            "wrote" " "
                                            (if markdownify? title-surround "'")
                                            (titlecase (shorten-title (get-clean-title-from-cite-info cite-map markdownify?)))
                                            (if markdownify? title-surround "'")
                                            (if (some? date) (str/join [" (" date ")"]) "")
                                            "?"])
                      :answer (get-clean-long-author-from-cite-info cite-map markdownify?)}))
    questions))
  ([questions cite-map]
   (add-raw-authorship-question questions cite-map false)))


(defn add-date-published-question
  "Append a raw question/answer pair about date published TODO"
  ([questions cite-map markdownify?]
   (let [title-surround (cond (= "book" (:itemType cite-map)) "***"
                              :else "'")
         date (re-find #"\d{4}" (:date cite-map))]
     (if (some? date)
      (conj questions {:question (str/join [(if markdownify? "*When*" "When") " "
                                            "was" " "
                                            (get-clean-long-author-from-cite-info cite-map markdownify?)
                                            "'s" " "
                                            (if markdownify? title-surround "'")
                                            (titlecase (shorten-title (get-clean-title-from-cite-info cite-map markdownify?)))
                                            (if markdownify? title-surround "'")
                                            " " "first" " "
                                            (if markdownify? "*published*" "published")
                                            "?"])
                       :answer (str/join [(if markdownify? "**") date (if markdownify? "**")])})
     questions)))
  ([questions cite-map]
   (add-date-published-question questions cite-map false)))


(defn add-journal-publisher-question
  "Append a raw question/answer pair about journal publisher TODO"
  ([questions cite-map markdownify?]
   (if (and (= "journalArticle" (:itemType cite-map))
            (> (count (into [] (filter #(= "author" (:creatorType %)) (:creators cite-map)))) 0)
            (some? (:publicationTitle cite-map)))
     (conj questions {:question (str/join ["What" " "
                                           (if markdownify? "*" "")
                                           "journal"
                                           (if markdownify? "*" "")
                                           " " "first" " "
                                           (if markdownify? "*" "")
                                           "published"
                                           (if markdownify? "*" "")
                                           " "
                                           (get-clean-long-author-from-cite-info cite-map markdownify?)
                                            "'s" " " "'"
                                            (titlecase (shorten-title (get-clean-title-from-cite-info cite-map markdownify?)))
                                            "'"
                                            "?"])
                       :answer (str/join [(if markdownify? "*") (:publicationTitle cite-map) (if markdownify? "*")])})
     questions))
  ([questions cite-map]
   (add-journal-publisher-question questions cite-map false)))


(defn format-block-quote "TODO"
  [input-lines]
  (str/join [(str/trim (str/join (map #(str/join "" ["> " % "\n"]) (str/split-lines (str/join input-lines))))) " ([TKAUTHOR](TKCITATION))"]))



(defn generate-short-sentence-blackouts
  "Take a string and return a list of raw front/back questions for all 'short sentences' apparently in the string. Always returns a vector, sometimes empty."
  ([s previous-line next-line]
  (let [sentences (str/split s #"(?<=[!?.]) ") ;; all the sentences in the string
        contexted-sentences (contextify sentences) ;; a list of all the sentences with their context
        rejoin-sentences #(str/trim (str/join " " (into [] (remove nil? %)))) ;; a hyper-specific joining function we'll use
        rejoin-lines #(str/trim (str/join "\\n> \\n" (into [] (remove nil? %)))) ;; a hyper-specific joining function we'll use
        wc #(count (str/split % #"\W+"))] ;; word count util function
    (->> contexted-sentences
         (filter #(< (wc (:target %)) 10))
         (map (fn [x] {:front
                       (rejoin-lines (concat previous-line [(rejoin-sentences (concat (:before x) [(blackout (:target x))] (:after x)))] next-line))
                       :back
                       (rejoin-lines (concat previous-line [(rejoin-sentences (concat (:before x) [(:target x)] (:after x)))] next-line))})))))
  ([s]
   (generate-short-sentence-blackouts s nil nil)))

(defn generate-phrase-blackouts
  "Take a string and return a list of raw front/back questions for all 'phrases' apparently in the string. Always returns a vector, sometimes empty."
  ([s previous-line next-line]
  (let [phrases (str/split s #"((?<=[!?\.,:;\'\"\(\)]) | (?=[!?\.,:;\'\"\(\)]))") ;; all the phrases in the string
        contexted-phrases (contextify phrases) ;; a list of all the phrases with their context
        rejoin-phrases #(str/trim (str/join " " (into [] (remove nil? %)))) ;; a hyper-specific joining function we'll use
        rejoin-lines #(str/trim (str/join "\\n> \\n" (into [] (remove nil? %)))) ;; a hyper-specific joining function we'll use
        wc #(count (str/split % #"\W+"))] ;; word count util function
      (->> contexted-phrases
           (filter #(< (wc (:target %)) 10))
           (filter #(> (wc (:target %)) 1))
           (map (fn [x] {:front
                         (rejoin-lines (concat previous-line [(rejoin-phrases (concat (:before x) [(blackout (:target x))] (:after x)))] next-line))
                         :back
                         (rejoin-lines (concat previous-line [(rejoin-phrases (concat (:before x) [(:target x)] (:after x)))] next-line))})))))
  ([s]
   (generate-phrase-blackouts s nil nil)))

(defn generate-line-broken-dirty-cloze-questions
  "Take a multi-line input quote and output a set of blackout cloze questions for each line separately (not displaying other lines of the quote!) This breaks poetry memorization, but works great for prose. TODO"
  [raw-string]
  (let [lines (str/split-lines raw-string)]
    (->> lines
         (map (fn [line]
                (concat (generate-short-sentence-blackouts line)
                        (generate-phrase-blackouts line)
                          )))
         flatten)))

(defn generate-line-blackout
  "TODO"
  [before-lines target-line after-lines]
  (let [rejoin-lines #(str/trim (str/join "\\n> \\n" (into [] (remove nil? %))))] ;; a hyper-specific joining function we'll use
    [{:front (rejoin-lines (concat before-lines [(blackout target-line)] after-lines))
      :back (rejoin-lines (concat before-lines [target-line] after-lines))}]))

(defn generate-line-contextualized-dirty-cloze-questions
  "TODO"
  [raw-string]
  (let [lines (str/split-lines raw-string)
        wc #(count (str/split % #"\W+"))
        contexted-lines (filter #(< 2 (wc (:target %)) 14) (contextify lines 1))
        long-contexted-lines (filter #(< 2 (wc (:target %)) 14) (contextify lines 2))]
    (into []
          (concat
    (->> contexted-lines
         (map (fn [line-in-context]
                (concat (generate-short-sentence-blackouts (:target line-in-context) (:before line-in-context) (:after line-in-context))
                        (generate-phrase-blackouts (:target line-in-context) (:before line-in-context) (:after line-in-context))
                        )))
         flatten)
    (->> long-contexted-lines
         (map (fn [line-in-context]
                (concat
                        (generate-line-blackout (:before line-in-context) (:target line-in-context) (:after line-in-context))
                        )))
         flatten)))))

(defn add-dirty-cloze-questions
  "Append blackout cloze questions to question list TODO"
  [questions raw-string]
  (into questions (concat (generate-line-broken-dirty-cloze-questions raw-string)
                          (generate-line-contextualized-dirty-cloze-questions raw-string))))

(defn build-question-map "TODO" [question answer]
  {:question {:question question
              :answer answer}})

(defn build-front-back-question-map "TODO" [front back]
  (let [fix-as-quote (fn [s] (if-not (nil? (some #{">"} s))
                                (format-block-quote (str/split-lines s))
                                s))]
    (if (and (> (count front) 5) (> (count back) 5))
      {:question {:front (fix-as-quote front)
                  :back (fix-as-quote back)}}
      nil)))

(defn citoid->questions
  "Take the (proccessed/map) returns from a citoid query and translate into a list of basic questions. TODO"
  [cite-map]
  (if (some? cite-map)
    (into []
          (map (fn [x] (build-question-map (:question x) (:answer x)))
              (remove nil?
                      (-> []
                          (add-date-published-question cite-map true)
                          (add-journal-publisher-question cite-map true)
                          (add-raw-authorship-question cite-map true)))))
    nil))

(defn clean-quote-cite-formatting [qn-map]
  (let [old-front (get-in qn-map [:question :front])
        old-back (get-in qn-map [:question :back])]
    (-> qn-map
        (assoc-in [:question :front] (str/replace old-front " ([TK" " \\n\\n([TK"))
        (assoc-in [:question :back] (str/replace old-back " ([TK" " \\n\\n([TK")))))

(defn quote->questions
  "Take raw input lines that are a quote and return a list of dirty cloze questions"
  [input-lines]
  (into []
        (distinct
         (map clean-quote-cite-formatting
         (remove #(str/includes? (get-in % [:question :front] "") "> ([TK")
                 (map (fn [x] (build-front-back-question-map (str/replace (format-block-quote (:front x)) "\"" "\\\"")
                                                             (str/replace (format-block-quote (:back x)) "\"" "\\\"")))
                      (remove nil?
                              (-> []
                                  (add-dirty-cloze-questions (str/join "\n" input-lines))))))))))

(defn add-quote-questions
  "Add dirty cloze questions for a quote post"
  [post-header input-lines]
  (if (= "quote" (:category post-header))
    (-> post-header
        (assoc :questions (quote->questions input-lines)))
    post-header))

(defn add-bibliographic-questions "TODO" [post-header]
  (if-let [clean-id (first (remove nil? [(:doi post-header) (:isbn post-header)]))]
    (let [citation-info (first (json/parse-string (query-and-clean-cite-data clean-id "zotero") true))]
      (-> post-header
          ;; (assoc :citation_info citation-info)
          (assoc :questions (citoid->questions citation-info))))
    post-header))

(defn replace-empty-questions "TODO" [post-header]
  (update-in post-header [:questions] (fn [qs] (if (empty? qs)
                                                 nil
                                                 qs))))

(defn construct-post-header "Construct a post header from command line input lines" [input-lines]
  (-> {:layout "post"
       :date (get-datetime-now)
       :published false
       :category (get-best-post-category input-lines)
       :body (if (= (get-best-post-category input-lines) "quote")
               (format-block-quote (str/join "\n" input-lines))
               "")}
      (add-reading-dates-and-rating-fields)
      (add-raw-citation-ids input-lines)
      (add-resolved-citation-info)
      (add-metaculus-metadata input-lines)
      (add-best-permalink input-lines)
      (add-best-title input-lines)
      (add-bibliographic-questions)
      (add-quote-questions input-lines)
      (replace-empty-questions)
      (assoc :epistemicstatus "TK")
      (assoc :location (get-current-location))
      (remove-nils)))

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
        questions (:questions post-header)
        epistatus (:epistemicstatus post-header)
        title (:title post-header)
        clean-header (dissoc post-header :category :body :questions :epistemicstatus :title)]
    (str/join ["---" "\n"
               (if (some? title) (str/join ["title: \"" title "\"\n"]) "")
               (yaml/generate-string clean-header
                                     :dumper-options {:flow-style :block})
               (str/join ["categories: ['" categories "']\n"])
               (str/join ["epistemicstatus: \"" epistatus "\"\n"])
               (if (some? questions) (yaml/generate-string {:questions questions} :dumper-options {:flow-style :block}) "")
               "---" "\n" "\n"])))

(defn clean-up-yaml-header-string
  "Take a generated yaml header string and run some very rough cleans on it"
  [s]
  (-> s
      (str/replace "''" "'")
      (str/replace #"(?<=\squestion:) '" " \"")
      (str/replace #"(?<=\sanswer:) '" " \"")
      (str/replace #"(?<=\sfront:) '" " \"")
      (str/replace #"(?<=\sback:) '" " \"")
      (str/replace "'\n" "\"\n")
      (str/replace #"(?<=\squestion:) (?=[^'])" " \"")
      (str/replace #"(?<=\sanswer:) (?=[^'])" " \"")
      (str/replace #"(?<=\sfront:) (?=[^'])" " \"")
      (str/replace #"(?<=\sback:) (?=[^'])" " \"")
      (str/replace "*\n" "*\"\n")
      (str/replace "?\n" "?\"\n")
      (str/replace "\"\"" "\"")
      ))

(defn build-full-file-string
  "Take a post header and construct a complete file string"
  [post-header]
  (let [clean-body (:body post-header)]
    (str/join [(clean-up-yaml-header-string (build-yaml-header-string post-header))
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
        file-text (build-full-file-string post-header)
        existing-paths (list-existing-matching-files base-path post-header)]
    (cond (not= 0 (count existing-paths)) ; if a matching path already exists, just print the first
          (println (first existing-paths))
          (and (= 0 (count existing-paths)) (some? (:title post-header))) ; if no path, and a valid title exists, print the header and outpath
          (do (spit outpath file-text) (println outpath))
          :else (println ""))))







(defn test-display-proposed-header "TODO" [input-lines notes-directory]
  (if (> (count (str/join input-lines)) 0)
    (->> input-lines
         (construct-post-header)
         (build-full-file-string)
         println)
    (println "")))
;; (test-display-proposed-header input-lines base-notes-directory)

;; MAIN - ACTUALLY RUN THIS BASTARD
(defn -main "TODO" [input-lines notes-directory]
  (if (> (count (str/join input-lines)) 0)
    (->> input-lines
        (construct-post-header)
        (safe-create-file-with-header base-notes-directory))
    (println "")))
(-main input-lines base-notes-directory)
