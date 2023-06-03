#!/usr/bin/env bb
;; a babashka script for logging items to add to my 'wishlist'
(import
 'java.time.LocalDateTime)

(require '[babashka.process :as p]
         '[clojure.java.io :as io]
         '[clojure.tools.cli :refer [parse-opts]]
         '[cheshire.core :as json]
         '[babashka.fs :as fs]
         '[clj-yaml.core :as yaml]
         '[clojure.string :as str])

(def cli-options
  [["-n" "--name NAME" "Name of the item"
    :default ""]
   ["-l" "--link LINK" "Link to the item"
    :default ""]
   ["-p" "--price PRICE" "Price of the item"
    :default 0]
   ["-c" "--currency CURRENCY" "Currency of the item"
    :default "AUD"]
   ["-t" "--note NOTE" "Notes about the item"
    :default ""]])

(defn get-existing-file
  "Return the first existing file from a list of paths."
  [paths]
  (->> paths
       (map #(fs/expand-home %))
       (filter fs/exists?)
       (map str)
       (first)))

(defn prompt-for-input
  "Prompt the user for input, with a default value. Return the input.
   Don't give a newline after the prompt, so that the user can type on the same line."
  [question default-value]
  (if (not (empty? default-value)) ;; if there's a default value, print it
    (print (str question " [" default-value "]: "))
    (print (str question ": "))) ;; otherwise, just print the question
  (flush) ;; flush the output stream, so that the prompt is printed
  (let [response (str (.readLine *in*))] ;; get input from the console
    response)) ;; return the input

(defn append-to-file [path data]
  (let [existing-data (yaml/parse-string (slurp path))
        new-data (conj existing-data data)]
    (spit path (yaml/generate-string new-data :dumper-options {:flow-style :block}))))

(defn -main [& args]
  (let [{:keys [arguments options errors]} (parse-opts (first args) cli-options)
        path (get-existing-file ["~/Documents/blog/_data/wishlist.yml"
                                 "~/Documents/blog/_data/wishlist.yaml"
                                 "~/blog/_data/wishlist.yml"
                                 "~/blog/_data/wishlist.yaml"
                                 "~/.wishlist.yml"
                                 "~/.wishlist.yaml"])]
    (if (not path)
      (do  ;; if the file doesn't exist, error out
        (println "Warning: wishlist file not found!")
        (System/exit 1))
      ;; otherwise, prompt for input as necessary
      (let [name (if (empty? (get options :name))
                   (prompt-for-input "Name" "")
                   (get options :name))
            link (if (empty? (get options :link))
                   (prompt-for-input "Link" "")
                   (get options :link))
            price (if (or (= 0 (get options :price))
                          (empty? (get options :price)))
                    (prompt-for-input "Price" "")
                    (get options :price))
            ;; ensure that price is appended to file as an integer
            price (if (number? price)
                    price
                    (Double/parseDouble price))
            price (int price)
            currency (get options :currency)
            note (if (empty? (get options :note))
                   (prompt-for-input "Note" "")
                   (get options :note))
            ;; get the current YYYY-MM-DD (don't need the time!)
            date (-> (str (LocalDateTime/now))
                     (str/split #"T")
                     (first))
            data {:name name
                  :link link
                  :price price
                  :currency currency
                  :note (if (empty? note) nil note)
                  :date date}
            ;; remove any keys with nil values
            data (->> data
                      (filter (fn [[k v]] (not (nil? v)))))]
        ;; append the new item to the file
        (append-to-file path data)))))

(-main *command-line-args*)