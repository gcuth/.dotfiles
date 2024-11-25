#!/usr/bin/env bb
;;
;; A dirty wrapper around a hyperbolic.xyz API endpoint. Used for generating a
;; text completion from an LLM base model. The script takes one or more paths
;; to text/markdown files, reads their contents, cleans them up, stitches them
;; together, and sends the resulting text to the API to generate a completion.
;; We print the result to stdout (with the completion text bolded), then exit.
;;
;; Usage: ./shoggoth.bb <path> [<path> ...]

(require '[babashka.fs :as fs]
         '[clojure.string :as str]
         '[babashka.http-client :as http]
         '[cheshire.core :as json])

(defn- get-api-key
  "Get the API key from the environment."
  []
  (System/getenv "HYPERBOLIC_API_KEY"))

(defn- is-valid-file?
  "Check for an accessible file with the right extension that exists at path."
  [path]
  (and (fs/exists? path)
       (fs/readable? path)
       (fs/regular-file? path)
       (or (= (fs/extension path) "txt")
           (= (fs/extension path) "md"))))

(defn- has-frontmatter?
  "Check if a file has yaml frontmatter."
  [path]
  (let [contents (slurp path)]
    (and (re-find #"---\n" contents)
         (str/starts-with? contents "---\n"))))

(defn- extract-frontmatter
  "Given a path, extract the yaml frontmatter from the file."
  [path]
  (if (has-frontmatter? path)
    (-> (slurp path)
        (str/split #"---\n")
        (second)
        (str/trim))
    ""))

(defn- extract-title
  "Given a path, extract the title (if any) from the file's yaml frontmatter."
  [path]
  (let [frontmatter (extract-frontmatter path)
        title-string (re-find #"title: (.*)" frontmatter)]
    (if (and (not (empty? frontmatter)) title-string)
      (str/trim (second title-string))
      "")))

(defn- extract-body
  "Given a path, extract the body of the file, removing any yaml frontmatter."
  [path]
  (if (has-frontmatter? path)
    (str/join "---\n"
              (-> (slurp path)
                  (str/split #"---\n")
                  (rest)
                  (rest)))
    (slurp path)))

(defn- fp->str
  "Read the contents of a file at path, clean it, and return as a string."
  [path]
  (let [title (extract-title path)
        title (if (empty? title) (fs/strip-ext (fs/file-name path)) title)
        body (extract-body path)]
    (str/trim (str/join "\n\n" [(str "# " title) body]))))

(defn- stitch-files
  "Given a list of paths, read the contents of each file, clean them, and return as a single string."
  [files]
  (str/join "\n\n" (map fp->str files)))

(defn- completion
  "Given a string, send it to the hyperbolic API, returning a completion.
   
   Options map supports:
   - :api-token (required) - Your Hyperbolic API key
   - :prompt (required) - The input text / prompt

   - :model (optional) - Model to use; default 'meta-llama/Meta-Llama-3.1-405B'
   - :max-tokens (optional) - Maximum number of tokens to generate; default 512
   - :temperature (optional) - The temperature for sampling; default 0.7
   - :top-p (optional) - The nucleus sampling parameter; default 0.9
   - :stop (optional) - Stop token; default nil
   "
  [{:keys [api-token prompt model max-tokens temperature top-p stop]
    :or {model "meta-llama/Meta-Llama-3.1-405B"
         max-tokens 512
         temperature 0.7
         top-p 0.9
         stop nil}}]
  (when-not api-token
    (throw (ex-info "No API key provided!" {:error :missing-api-key})))
  (when-not prompt
    (throw (ex-info "No prompt provided!" {:error :missing-prompt-prompt})))

  (let [data {:prompt prompt
              :model model
              :max_tokens max-tokens
              :temperature temperature
              :top_p top-p
              :stream false
              :n 1}
        data (if stop (assoc data :stop stop) data)
        resp (http/post "https://api.hyperbolic.xyz/v1/completions"
                        {:headers {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " api-token)}
                         :body (json/generate-string data)})]
    :body
    (json/parse-string true)
    :choices
    first
    :text))

(defn -main
  "Run the script with the given command-line arguments.
   Usage: ./shoggoth.bb <path> [<path> ...]
   
   Paths should be to text or markdown files.
   
   The script will filter out any invalid files, read & sanitize the contents,
   stitch them together, send them to the API, and print the completion using
   ANSI escape codes to bold the completion text."
  [& args]
  (let [files (->> args
                   (map fs/expand-home)
                   (map fs/unixify)
                   (filter is-valid-file?))
        api-token (get-api-key)]
    (when (empty? files)
      (println "Plz provide valid paths to .txt and/or .md files!")
      (println "Usage: ./shoggoth.bb <path> [<path> ...]")
      (System/exit 1))
    (when (nil? api-token)
      (println "No API key found in HYPERBOLIC_API_KEY environment variable.")
      (println "Plz set it & try again.")
      (System/exit 1))
    (let [prompt (stitch-files files)
          result (completion {:prompt prompt
                              :api-token api-token
                              :max-tokens 128
                              :stop "\n\n"})]
      (println (str prompt "\033[1m" result "\033[0m")))))

;; If we're running this script directly, call -main with the command-line args
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*)) 