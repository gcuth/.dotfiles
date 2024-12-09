#!/usr/bin/env bb
;;
;; A script for generating, validating, & reading Personal Project IDs.
;; (Don't ask me what they are; I just work here.)
;;
;; This can be done fully interactively, or entirely via CLI arguments.

(require '[clojure.string :as str]
         '[clojure.instant :as inst]
         '[clojure.set :as set]
         '[babashka.cli :as cli])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;; CONSTANTS & GLOBAL DEFAULTS ;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private DOB ;; Default 'epoch'; your birth datetime. (RFC3339 format)
  "1993-02-10T13:12:30")

(def ^:private NOW ;; Default new project datetime. Right now! (RFC3339 format)
  (first (str/split (.toString (java.time.Instant/now)) #"\.")))

(def ^:private PPID-RE ;; Default regex pattern for validating a PPID string.
  #"^([0-9BCDEFGHJKMNPQRSTUVWXYZ]{4}[-\s_:.]?){4}$")

(def ^:private alphabet  ;; GeoHash alphabet
  (vec "0123456789bcdefghjkmnpqrstuvwxyz"))

(def ^:private decode-gh-char ;; Decode a character from the GH alpha to 0-31.
  (into {} (for [i (range (count alphabet))]
             [(alphabet i) i])))

(def ^:private encode-gh-char ;; Encode a number from 0-31 to the GH alpha.
  (set/map-invert decode-gh-char))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CONVENIENCE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- zpad
  "Zero-pad a string to a given length."
  [s n]
  (str (apply str (repeat (- n (count s)) "0")) s))

(defn- valid?
  "Validate a (PPID) string against a regex default."
  ([s] (some? (re-matches PPID-RE s)))
  ([re s] (some? (re-matches re s))))

(defn- hours-since
  "Calculate the number of hours between
   (a) the start datetime (given as a RFC3339 string), &
   (b) the current datetime (also given as a RFC3339 string)."
  ([] (hours-since DOB))
  ([start] (hours-since start NOW))
  ([start now]
   (int (/ (- (.getTime (inst/read-instant-date now))
              (.getTime (inst/read-instant-date start)))
           (* 1000 60 60)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CONVERSIONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- int->gh
  "Convert an integer into a GeoHash-alphabet base-32."
  ([n] (int->gh n alphabet))
  ([n alpha]
   (let [base (count alpha)]
     (loop [x n
            out ""]
       (if (zero? x)
         (if (empty? out) "0" out)
         (recur (quot x base)
                (str (nth alpha (mod x base)) out)))))))

(defn- gh->int
  "Convert a GeoHash-alphabet (base-32) string into an integer."
  ([s] (gh->int s alphabet))
  ([s alpha]
   (let [s (str/lower-case s) ;; ensure lowercase
         base (count alpha)]
     (reduce (fn [acc c]
               (+ (* acc base)
                  (.indexOf alpha c)))
             0
             s))))

(defn- int->bin
  "Convert an integer into a binary string."
  [n]
  (Integer/toString n 2))

(defn- bin->int
  "Convert a binary string into an integer."
  [s]
  (Integer/parseInt s 2))

(defn- answer->bin
  "Convert a one-character string (representing an answer to a multiple-choice
   question) into a zero-padded binary string. Note that you must also provide
   a string representing the ordered list of possible answers.
   eg, (answer->binary \"A\" \"ABCD\") => \"00\"
       (answer->binary \"C\" \"ABCD\") => \"10\"
       (answer->binary \"Q\" \"ABCD\") => nil
       (answer->binary \"F\" \"ABCDEFGH\") => \"101\""
  [answer answers]
  (if (or (empty? answer) (empty? answers) (not= 1 (count answer)))
    nil
    (let [index (.indexOf (vec answers) (char (first answer)))
          n-bits (int (Math/ceil (Math/log (count answers))))]
      (if (neg? index)
        nil
        (zpad (int->bin index) n-bits)))))

(defn- bin->answer
  "Convert a binary string into a one-character string representing an answer
   to a multiple-choice question. Note that you must also provide a string
   representing the ordered list of possible answers.
   eg, (binary->answer \"00\" \"ABCD\") => \"A\"
       (binary->answer \"10\" \"ABCD\") => \"C\"
       (binary->answer \"101\" \"ABCDEFGH\") => \"ACE\"
       (binary->answer \"101\" \"ABCD\") => nil"
  [binary answers]
  (if (or (empty? binary)
          (empty? answers)
          (not= (count answers) (int (Math/pow 2 (count binary)))))
    nil
    (str (nth answers (bin->int binary)))))

(defn- bin->gh
  "Convert a binary string into GeoHash-alphabet base-32."
  ([s] (bin->gh s alphabet))
  ([s alpha]
   (if (empty? s)
     nil
     (zpad (int->gh (bin->int s))
           (count (int->gh (int (Math/pow 2 (count s)))))))))

(defn- gh->bin
  "Convert a GeoHash-alphabet base-32 string into a binary string."
  [s]
  (if (empty? s)
    nil
    (int->bin (gh->int s))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; GEOHASH FUNCTIONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- gh->long
  "Convert a GeoHash-alphabet string to a long number."
  [s]
  (reduce #(bit-or %2 (bit-shift-left %1 5)) (map decode-gh-char s)))

(defn- long->gh
  "Convert a long number `n` into a GeoHash string of length `l`."
  [n l]
  (->> (range (dec l) -1 -1)
       (map #(* 5 %))
       (map #(bit-and 2r11111 (bit-shift-right n %)))
       (map encode-gh-char)
       (apply str)))

(defn- gh-deinterleave
  "Deinterleave n into two numbers."
  [n]
  (let [get-num #(reduce (fn [acc i]
                           (if (bit-test % i)
                             (bit-flip acc (/ (dec i) 2))
                             acc))
                         0
                         (range 1 64 2))]
    [(get-num n)
     (get-num (bit-shift-left n 1))]))

(defn- subdivide
  "Subdivide an interval mapped by n, using each bit of n to determine which
   side of the interval to return."
  [[l h] n bits]
  (let [[r-l r-h] (reduce (fn [[l h] b]
                            (let [m (/ (+ h l) 2)]
                              (if (bit-test n b)
                                [m h]
                                [l m])))
                          [l h]
                          (range (dec bits) -1 -1))]
    (+ r-l (/ (- r-h r-l) 2))))

(defn- locate
  "Return the encoded number for the smallest interval containing the
  location represented in b bits."
  [[l h] loc bits]
  (first
   (reduce (fn [[acc [l h]] b]
             (let [m (+ l (/ (- h l) 2))]
               (if (<= loc m)
                 [acc [l m]]
                 [(bit-flip acc b) [m h]])))
           [0 [l h]]
           (range (dec bits) -1 -1))))

(defn- gh-interleave
  "Interleave x and y into a single number, setting the bits of x to the odd
   bits of the result, and the bits of y to the even bits of the result."
  [x y]
  (reduce
   (fn [acc b]
     (let [acc (if (bit-test x b) (bit-flip acc (inc (* b 2))) acc)
           acc (if (bit-test y b) (bit-flip acc (* b 2)) acc)]
       acc))
   0
   (range 30)))

(defn- location->gh
  "Encode a lat/long location as a geohash.
   Note: if no size size is specified, the default 12 is used."
  ([loc] (location->gh loc 12))
  ([[lat lng] size]
   (let [bits (long (/ (* 5 size) 2))
         lng-bits (if (odd? size) (inc bits) bits)
         lat-num (locate [-90.0 90.0] lat bits)
         lng-num (locate [-180.0 180.0] lng lng-bits)
         n (if (odd? size)
             (gh-interleave lat-num lng-num)
             (gh-interleave lng-num lat-num))]
     (long->gh n size))))

(defn- gh->location
  "Decode a geohash string into a location."
  [s]
  (let [s (take 12 s)
        [lat-num lng-num] (-> s (gh->long) (gh-deinterleave))
        len (count s)
        lat-bits (long (/ (* 5 len) 2))
        lng-bits (if (odd? len) (inc lat-bits) lat-bits)
        lat (subdivide [-90.0 90.0] lat-num lat-bits)
        lng (subdivide [-180.0 180.0] lng-num lng-bits)]
    [lat lng]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;; QUESTIONS & CHOICES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private QUESTIONS
  [{:topic "virtue"
    :desc (str "What its core virtue is:"
               " 0 = truth;"
               " 1 = beauty;"
               " 2 = love;"
               " 3 = metis.")
    :question "What **virtuous quality** will this thing support?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["**Truth**: Knowledge, Reference, & Learning"
              "**Beauty**: Aesthetics, Play"
              "**Love**: Human Connection"
              "**Metis**: Practical Wisdom & Craftsmanship"]}
   
   {:topic "size"
    :desc (str "How big it will be:"
               " 0 = tiny;"
               " 1 = medium;"
               " 2 = large;"
               " 3 = massive.")
    :question "What is the intended **scale/size** of this thing?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["**Tiny**: hand-sized; a single page."
              "**Medium**: a bread-box; an essay's worth."
              "**Large**: contained to a room; a book's worth."
              "**Massive**: these (walls|pages) are to narrow to contain it."]}
   
   {:topic "lifespan"
    :desc (str "How long it will last:"
               " 0 = minutes;"
               " 1 = hours/days;"
               " 2 = months/years;"
               " 3 = a lifetime.")
    :question "How long do you hope this will **last**?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["**Minutes/Moments**"
              "**Hours or Days**"
              "**Months or Years**"
              "**A Lifetime**"]}
   
   {:topic "home"
    :desc (str "Where it will live:"
               " 0 = everywhere/nowhere;"
               " 1 = body;"
               " 2 = world;"
               " 3 = gallery.")
    :question "Where will this thing **live**?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["**Everywhere**: Cyberspace, or On The Wind"
              "**The Body**: in, on, or near"
              "**The Everyday World**"
              "**An Exhibition Space**: Gallery, Theatre, etc."]}
   
   {:topic "manifestation"
    :desc (str "How it will manifest:"
               " 0 = concrete;"
               " 1 = abstract.")
    :question "Will this thing be **concrete** or **abstract**?"
    :validate (fn [v] (some #{v} [0 1]))
    :choices ["**Concrete**: Physical"
              "**Abstract**: Ephemeral"]}
   
   {:topic "feel"
    :desc (str "How it will feel to others:"
               " 0 = soft;"
               " 1 = hard;"
               " 2 = both;"
               " 3 = neither.")
    :question "How do you imagine the thing will **feel** to others?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["**Soft**"
              "**Hard**"
              "**Both**"
              "**Neither**"]}
   
   {:topic "precision"
    :desc (str "How precise making it will be:"
               " 0 = messy;"
               " 1 = minimal;"
               " 2 = exacting;"
               " 3 = atomic.")
    :question "How much **precision** will be required?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["**lol none**: Shockingly Little; Zeroeth Draftishness"
              "**Un Poco?**: Hand Tools; Minimal Edits & Checks"
              "**Dwarvish**: CNC Tooling; Adversarial Edits/Tests/Checks"
              "**Work@JPL**: Anal Atomic Engineering & A Lawyerly Attitude"]}
   
   {:topic "time"
    :desc (str "How much time it will take:"
               " 0 = <1 day;"
               " 1 = <1 week;"
               " 2 = <1 month;"
               " 3 = more.")
    :question "How much **time** do you expect will be needed to make this?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["Less Than A **Day**"
              "Less Than A **Week**"
              "Less Than A **Month**"
              "A Dedicated **Year (Or More)**"]}
   
   {:topic "audience"
    :desc (str "Access restrictions:"
               " 0 = top secret;"
               " 1 = secret;"
               " 2 = confidential;"
               " 3 = unrestricted.")
    :question "What are the **access restrictions**? Who is this for?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["**Top Secret**: Just Me"
              "**Secret**: Me & One Other"
              "**Confidential**: Friends & Colleagues & Samizdat Networks"
              "**Unrestricted**: The Public"]}
   
   {:topic "why"
    :desc (str "Why you're doing it:"
               " 0 = love;"
               " 1 = curiosity;"
               " 2 = dissatisfaction;"
               " 3 = external reward.")
    :question "Why do you feel **visceral motivation** to pursue this?"
    :validate (fn [v] (some #{v} [0 1 2 3]))
    :choices ["**Love** / Metta / Generosity"
              "**Curiosity** / Hyperfocus / Play"
              "**Dissatisfaction** With The World Without"
              "**Financial / Social Status Rewards** Expected"]}])

(defn- render-for-cli
  "Given a string (ie, a question or answer option), render it for the CLI.
   
   Replace any markdown-style **bold** with ANSI bold codes."
  [s]
  (str/replace s #"\*\*(.*?)\*\*" "\033[1m$1\033[0m"))

(defn- prompt
  "Prompt the user with a given question & set of choices."
  [{:keys [question choices]}]
  (print
   (str/join "\n"
             [(render-for-cli question)
              (str/join "\n" (map-indexed (fn [i c]
                                            (str i ". " (render-for-cli c)))
                                          choices))
              (str "Please enter the number of your choice ["
                   "0-" (dec (count choices)) "]: ")]))
  (flush)
  (try (Integer/parseInt (read-line))
       (catch Exception e
         (println "Invalid input. Please enter a number!")
         (System/exit 1))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;; PPID: VALIDATION & GENERATION & PARSING ;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; VALIDATION -----------------------------------------------------------------
;; TODO: Write a neat validation function that takes a PPID string & checks it!

;; GENERATION -----------------------------------------------------------------
(defn- info->ppid
  "Create a PPID string from a given set of inputs.
   
   The inputs are expected to be a map with the following keys:
   - :datetime  = a RFC3339 string representing the project's starting datetime
   - :epoch     = a RFC3339 string representing the epoch datetime)
   - :latitude  = a double representing the project's starting latitude
   - :longitude = a double representing the project's starting longitude
   - :answers   = an ordered sequence of 10 vectors, each containing:
                (1) a single-character string (representing a given answer), &
                (2) a string representing an ordered list of possible answers.
   
   The output is a 16-character PPID (or nil if any of the inputs are invalid).
   This is composed of:
    - the first four characters of the hours since epoch (as base-32)
    - the first two characters of the location hash (as base-32)
    - alternating characters of the project info & remaining location hash
    - the last two characters of the hours since epoch.
   This order maximises the stability of the *start* of the PPID string, while
   also allowing for visually distinctive & varying characters throughout."
  [{:keys [datetime epoch latitude longitude answers]
    :or {epoch DOB
         datetime NOW}}]
  {:pre [(= 10 (count answers))
         (every? #(= 2 (count %)) answers)
         (every? #(= 1 (count (first %))) answers)
         (= 1 (count (filter #(= 2 (count (second %))) answers)))
         (= 9 (count (filter #(= 4 (count (second %))) answers)))]}
  (let [project-time (zpad (int->gh (hours-since epoch datetime)) 6)
        project-info (zpad (bin->gh
                            (zpad (str/join "" (map (fn [[a as]]
                                                      (answer->bin a as))
                                                    answers))
                                  19)) 4)
        latlong-hash (location->gh [latitude longitude] 6)]
    (when (every? identity [project-time project-info latlong-hash]) ;; no nils
      (->> [(subs project-time 0 4) ;; first 4 chars of time (extremely stable)
            (subs latlong-hash 0 2) ;; first 2 chars of latlong-hash ( ±630km)
            ;; interleave the project-info with remaining 4 latlong-hash chars:
            (str/join "" (interleave project-info (subs latlong-hash 2 6)))
            (subs project-time 4 6)] ;; +last 2 characters of project time
           (str/join "")
           (partition 4)
           (map (partial apply str))
           (str/join "-")
           (str/upper-case)))))

(defn generate
  "Generate a PPID string, interactively or otherwise, as opts allow."
  [{:keys [datetime epoch latitude longitude        ;; ordinary metadata
           virtue size lifespan home manifestation  ;; question answers
           feel precision time audience why] ;; question answers
    }]
  (let [virtue (or virtue (prompt (first (filter #(= "virtue" (:topic %)) QUESTIONS))))
        size (or size (prompt (first (filter #(= "size" (:topic %)) QUESTIONS))))
        lifespan (or lifespan (prompt (first (filter #(= "lifespan" (:topic %)) QUESTIONS))))
        home (or home (prompt (first (filter #(= "home" (:topic %)) QUESTIONS))))
        manifestation (or manifestation (prompt (first (filter #(= "manifestation" (:topic %)) QUESTIONS))))
        feel (or feel (prompt (first (filter #(= "feel" (:topic %)) QUESTIONS))))
        precision (or precision (prompt (first (filter #(= "precision" (:topic %)) QUESTIONS))))
        time (or time (prompt (first (filter #(= "time" (:topic %)) QUESTIONS))))
        audience (or audience (prompt (first (filter #(= "audience" (:topic %)) QUESTIONS))))
        why (or why (prompt (first (filter #(= "why" (:topic %)) QUESTIONS))))
        get-ans (fn [t] (->> QUESTIONS
                             (filter #(= t (:topic %)))
                             (first)
                             (:choices)
                             (count)
                             (range)
                             (apply str)))
        answers [[(str virtue) (get-ans "virtue")]
                 [(str size) (get-ans "size")]
                 [(str lifespan) (get-ans "lifespan")]
                 [(str home) (get-ans "home")]
                 [(str manifestation) (get-ans "manifestation")]
                 [(str feel) (get-ans "feel")]
                 [(str precision) (get-ans "precision")]
                 [(str time) (get-ans "time")]
                 [(str audience) (get-ans "audience")]
                 [(str why) (get-ans "why")]]]
    (try (info->ppid {:datetime datetime
                      :epoch epoch
                      :latitude latitude
                      :longitude longitude
                      :answers answers})
         (catch Exception e
           (println "Error when attempting to generate PPID!")
           (System/exit 1)))))

;; PARSING --------------------------------------------------------------------
;; TODO: Write a neat parsing function that takes a PPID & returns a map!

;; 0123-4567-89TE-TTFF
;; TTTT-LLIL-ILIL-ILTT
(defn ppid->info
  "Parse a provided PPID string and return a human-readable map of its parts."
  [ppid]
  (when (valid? ppid)
    (let [ppid (str/lower-case (str/trim (str/replace ppid #"[-\s_:.]" "")))
          ;; time = 0th, 1st, 2nd, 3rd, 14th & final characters of the PPID
          time (str/join "" [(subs ppid 0 4) (subs ppid 14 16)])
          ;; location = 4th, 5th, 7th, 9th, 11th, & 13th characters of the PPID
          geoh (str/join "" [(subs ppid 4 6)])])))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; -MAIN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-questions-as-opts
  "Given a vec of question/choice maps & a CLI spec map, add the questions as
   additional opts to the CLI spec. Each question is added as a map with the
   following keys:
   - 'topic' (as a keyword) as the top level key for each
   - :default nil as the default value for each
   - :coerce :int as the coercion function for each
   - :validate set to the question's :validate function"
  [spec questions]
  (->> questions
       (map (fn [q]
              {(keyword (:topic q)) {:default nil
                                     :ref (str "<" (:topic q) ">")
                                     :coerce #(Integer/parseInt %)
                                     :require false
                                     :validate (:validate q)
                                     :desc (:desc q)}}))
       (apply merge)
       (merge spec)))

(def cli-spec
  {:spec
   (add-questions-as-opts
    {:datetime {:ref "<datetime>"
                :desc "A project's inception datetime; right now! (In RFC3339.)"
                :coerce str
                :alias :d
                :default NOW
                :default-desc (str "\"" NOW "\"")
                :require false
                :validate (fn [v] true)}
     :epoch {:ref "<epoch>"
             :desc "An 'epoch' datetime from which to measure. (In RFC3339.)"
             :coerce str
             :alias :e
             :default DOB
             :default-desc (str "\"" DOB "\"")
             :require false
             :validate (fn [v] true)}
     :latitude {:ref "<latitude>"
                :desc "Geographic latitude at the project's inception."
                :coerce #(Float/parseFloat %)
                :default nil
                :require true
                :validate (fn [v] true)}
     :longitude {:ref "<longitude>"
                 :desc "Geographic longitude at the project's inception."
                 :coerce #(Float/parseFloat %)
                 :default nil
                 :require false
                 :validate (fn [v] true)}}
    QUESTIONS)
   :error-fn (fn [{:keys [spec type cause msg option] :as data}]
               (when (= :org.babashka/cli type)
                 (case cause
                   :require (println (format "Missing required option! %s" option))
                   :validate (println (format "Invalid option: %s\n" msg)))))})



(defn show-help
  [spec]
  (let [table (cli/format-opts (merge spec {:order (vec (keys (:spec spec)))}))]
    (->> (conj []
               ""
               "Usage: ./ppid.bb [command] <options>"
               "   eg, ./ppid.bb generate --latitude 11.455296790708907 --longitude -86.10409445582155"
               ""
               "Commands:"
               "  generate  Create a new PPID string from provided inputs."
               ""
               "Options:"
               table)
         (str/join "\n"))))

(defn -main
  "Generate a PPID string from a given set of inputs."
  [& args]
  (let [input (cli/parse-args args cli-spec)]
    ;; (println input)
    (cond (true? (:help (:opts input))) (println (show-help cli-spec))
          (= (:args input) ["help"]) (println (show-help cli-spec))
          (= (:args input) ["test"]) (println (prompt (first QUESTIONS)))
          (= (:args input) ["generate"]) (println (generate (:opts input)))
          :else (println "Invalid command; try 'help' for usage."))))

;; If we're running this script from the command line, run the -main function.
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))