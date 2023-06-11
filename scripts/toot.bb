#!/usr/bin/env bb
;; a babashka script for creating (and opening) new files for draft multi-part
;; posts on mastodon. Assumes you have a drafts directory in your iCloud folder.

(require '[babashka.fs :as fs]
         '[clj-yaml.core :as yaml]
         '[babashka.process :refer [shell]])

(defn get-now
  "Get the current datetime in the format YYYY-MM-DD HH:MM:SS TZ
   eg 2020-12-31 23:59:59 +1000"
  []
  (let [now (new java.util.Date)
        formatter (new java.text.SimpleDateFormat "yyyy-MM-dd HH:mm:ss Z")]
    (.format formatter now)))

(def icloud-post-dir "~/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/Mastodon/Drafts")

(def default-header {:published false
                     :date (get-now)})

(defn create-post-file
  "Create a new post file with a default header. By default, uses a uuid filename.
   Return the path to the new file.
   
   Takes optional args: title, directory, filename."
  [& {:keys [title directory filename]
      :or {title nil
           directory icloud-post-dir
           filename (str (java.util.UUID/randomUUID) ".md")}}]
  (let [path (fs/unixify (fs/expand-home (str directory "/" filename)))
        header (if title
                 (assoc default-header :title title)
                 default-header)]
    (spit path
          (str "---\n"
               (yaml/generate-string header :dumper-options {:flow-style :block})
               "---\n\n"))
    path))

(defn -main [& args]
  (let [open-to-edit (fn [p] (shell "open" p))
        post-file (create-post-file)]
    (open-to-edit post-file)))

(-main *command-line-args*)