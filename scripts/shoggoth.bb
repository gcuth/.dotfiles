#!/usr/bin/env bb
;;
;; A dirty wrapper around either
;; 1. a hyperbolic.xyz API endpoint, or
;; 2. Anthropic's Claude API.
;;
;; Used for generating a text completion from an LLM base model (in the case
;; of hyperbolic.xyz) or a custom prompt (in the case of Anthropic's Claude
;; API) intended to *simulate* as a base model.
;;
;; The script takes one or more paths to text/markdown files, reads their
;; contents, cleans them up, stitches them together, and sends the resulting
;; text to the API to generate a completion.
;; 
;; By default, we print the result to stdout (with the completion text bolded).
;;
;; Usage: ./shoggoth.bb <path> [<path> ...]

(require '[babashka.fs :as fs]
         '[clojure.string :as str]
         '[babashka.http-client :as http]
         '[babashka.cli :as cli]
         '[cheshire.core :as json])

(defn- get-hyperbolic-api-key
  "Get the API key from the environment."
  []
  (System/getenv "HYPERBOLIC_API_KEY"))

(defn- get-anthropic-api-key
  "Get the API key from the environment."
  []
  (System/getenv "ANTHROPIC_API_KEY"))

(defn- get-api-key
  "Get the API key from the environment."
  []
  (or (get-anthropic-api-key)
      (get-hyperbolic-api-key)))

(defn- is-hyperbolic?
  "Check if we're using the hyperbolic API."
  []
  (not (nil? (get-hyperbolic-api-key))))

(defn- is-anthropic?
  "Check if we're using the anthropic API."
  []
  (not (nil? (get-anthropic-api-key))))

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
  "Given a list of paths, read the contents of each file, clean them, then
   return as a single string."
  [files]
  (str/join "\n\n" (map fp->str files)))


(defn- hyperbolic-completion
  "Given a string, send it to the hyperbolic API, returning a completion.
   
   Options map supports:
   - :api-token (required) - Your API key
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
    (-> resp
        :body
        (json/parse-string true)
        :choices
        first
        :text)))

(defn- anthropic-completion
  "Given a string, send it to the anthropic API, returning a completion."
  [{:keys [api-token prompt model max-tokens temperature top-p stop]
    :or {model "claude-3-5-sonnet-20241022"
         max-tokens 1024
         temperature 0.8
         top-p 0.9
         stop nil}}]
  (let [data {:model model
              :max_tokens max-tokens
              :temperature temperature
              :system "The assistant is in CLI simulation mode, and responds to the user's commands only with the output of the command."
              :stop_sequences ["\n</output>"]
              :messages [{:role "user"
                          :content "<cmd>cat ./full_text.md</cmd>"}
                         {:role "assistant"
                          :content (str "<output>\n"
                                        (str/trim prompt))}]}
        data (if stop (assoc data :stop stop) data)
        resp (http/post "https://api.anthropic.com/v1/messages"
                        {:headers {"x-api-key" api-token
                                   "anthropic-version" "2023-06-01"
                                   "content-type" "application/json"}
                         :body (json/generate-string data)})]
    (-> resp
        :body
        (json/parse-string true)
        :content
        first
        :text)))



(defn -main
  "Run the script with the given command-line arguments.
   Usage: ./shoggoth.bb <path> [<path> ...] [--verbose]
   
   Paths should be to text or markdown files.
   
   The script will filter out any invalid files, read & sanitize the contents,
   stitch them together, send them to the API, and print the completion using
   ANSI escape codes to bold the completion text."
  [& args]
  (let [{:keys [verbose]} (cli/parse-opts args)
        files (->> args
                   (map fs/expand-home)
                   (map fs/unixify)
                   (filter is-valid-file?))
        api-token (get-api-key)
        type (if (is-anthropic?) "anthropic" "hyperbolic")]
    (when (empty? files)
      (println "Plz provide valid paths to .txt and/or .md files!")
      (println "Usage: ./shoggoth.bb <path> [<path> ...] [--verbose]")
      (System/exit 1))
    (when (nil? api-token)
      (println "No API key found in HYPERBOLIC_API_KEY or ANTHROPIC_API_KEY.")
      (println "Plz set one of them as an environment variable & try again.")
      (System/exit 1))
    (let [prompt (stitch-files files)
          result (if (is-anthropic?)
                   (anthropic-completion {:prompt prompt
                                          :api-token api-token
                                          :model "claude-3-5-sonnet-20241022"
                                          :max-tokens 1024
                                          :temperature 0.6
                                          :top-p 0.9})
                   (hyperbolic-completion {:prompt prompt
                                           :api-token api-token
                                           :model "meta-llama/Meta-Llama-3.1-405B"
                                           :max-tokens 512
                                           :temperature 0.7
                                           :top-p 0.9
                                           :stop "\n\n"}))]
      (if verbose
        (println (str prompt "\033[1m" result "\033[0m"))
        (println result)))))

;; If we're running this script directly, call -main with the command-line args
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*)) 