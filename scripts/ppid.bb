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

(def ^:private DOB ;; default epoch: birth datetime (in RFC3339 format)
  "1993-02-10T13:12:30")

(def ^:private alphabet  ;; GeoHash alphabet
  (vec "0123456789bcdefghjkmnpqrstuvwxyz"))

(def ^:private decode-gh-char ;; Decode a character from the GH alpha to 0-31.
  (into {} (for [i (range (count alphabet))]
             [(alphabet i) i])))

(def ^:private encode-gh-char ;; Encode a number from 0-31 to the GH alpha.
  (set/map-invert decode-gh-char))

(def ^:private PPID-RE ;; Default regex for validating a PPID string.
  #"^([0-9BCDEFGHJKMNPQRSTUVWXYZ]{4}[-\s_:.]?){4}$")


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
  ([start] (hours-since start (first (str/split (.toString (java.time.Instant/now)) #"\."))))
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
    :question "What **virtuous quality** will this thing support?"
    :choices ["**Truth**: Knowledge, Reference, & Learning"
              "**Beauty**: Aesthetics, Play"
              "**Love**: Human Connection"
              "**Metis**: Practical Wisdom & Craftsmanship"]}
   {:topic "size"
    :question "What is the intended **scale/size** of this thing?"
    :choices ["**Tiny**: hand-sized; a single page."
              "**Medium**: a bread-box; an essay's worth."
              "**Large**: contained to a room; a book's worth."
              "**Massive**: these (walls|pages) are to narrow to contain it."]}
   {:topic "lifespan"
    :question "How long do you hope this will **last**?"
    :choices ["**Minutes/Moments**"
              "**Hours or Days**"
              "**Weeks or Years**"
              "**A Lifetime**"]}
   {:topic "home"
    :question "Where will this thing **live**?"
    :choices ["**Everywhere**: Cyberspace, or On The Wind"
              "**The Body**: in, on, or near"
              "**The Everyday World**"
              "**An Exhibition Space**: Gallery, Theatre, etc."]}
   {:topic "manifestation"
    :question "Will this thing be **concrete** or **abstract**?"
    :choices ["**Concrete**: Physical"
              "**Abstract**: Ephemeral"]}
   {:topic "feel"
    :question "How do you imagine the thing will **feel** to others?"
    :choices ["**Soft**"
              "**Hard**"
              "**Both**"
              "**Neither**"]}
   {:topic "precision"
    :question "How much **precision** will be required?"
    :choices ["**lol none**: Shockingly Little; Zeroeth Draftishness"
              "**Un Poco?**: Hand Tools; Minimal Edits & Checks"
              "**Dwarvish**: CNC Tooling; Adversarial Edits/Tests/Checks"
              "**Work@JPL**: Anal Atomic Engineering & A Lawyerly Attitude"]}
   {:topic "time"
    :question "How much **time** do you expect will be needed to make this?"
    :choices ["Less Than A **Day**"
              "Less Than A **Week**"
              "Less Than A **Month**"
              "A Dedicated **Year (Or More)**"]}
   {:topic "audience"
    :question "What are the **access restrictions**? Who is this for?"
    :choices ["**Top Secret**: Just Me"
              "**Secret**: Me & One Other"
              "**Confidential**: Friends & Colleagues & Samizdat Networks"
              "**Unrestricted**: The Public"]}
   {:topic "motivation"
    :question "Why do you feel **visceral motivation** to pursue this?"
    :choices ["**Love** / Metta / Generosity"
              "**Curiosity** / Hyperfocus / Play"
              "**Dissatisfaction** With The World Without"
              "**Financial / Social Status Rewards** Expected"]}])




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;; PPID: GENERATION & VALIDATION & PARSING ;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn generate
  "Generate a PPID string from a given set of inputs.
   
   The inputs are expected to be a map with the following keys:
   - :now     = a RFC3339 string representing the current datetime.
   - :epoch   = a RFC3339 string representing the epoch datetime (ie, your DOB)
   - :lat     = a double representing the current latitude
   - :lng     = a double representing the current longitude
   - :answers = an ordered sequence of 10 vectors, each containing:
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
  [{:keys [now epoch lat lng answers]
    :or {epoch DOB
         now (first (str/split (.toString (java.time.Instant/now)) #"\."))}}]
  {:pre [(= 10 (count answers))
         (every? #(= 2 (count %)) answers)
         (every? #(= 1 (count (first %))) answers)
         (= 1 (count (filter #(= 2 (count (second %))) answers)))
         (= 9 (count (filter #(= 4 (count (second %))) answers)))]}
  (let [project-time (zpad (int->gh (hours-since epoch now)) 6)
        project-info (zpad (bin->gh
                            (zpad (str/join "" (map (fn [[a as]]
                                                      (answer->bin a as))
                                                    answers))
                                  19)) 4)
        latlong-hash (location->gh [lat lng] 6)]
    (when (every? identity [project-time project-info latlong-hash]) ;; all valid & not nil
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




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; -MAIN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def)