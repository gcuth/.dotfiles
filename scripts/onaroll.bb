#!/usr/bin/env bb
;;
;; a script for detecting whether we're 'on a roll' with writing, coding, or
;; some other kind of deep work. the idea is to run this script every 5 minutes
;; and then use the output to activate a 'Deep Work' focus mode on macos.

(require '[babashka.fs :as fs]
         '[babashka.cli :as cli]
         '[clojure.string :as str]
         '[babashka.process :refer [shell]])

(def THESIS-DIR "~/Documents/thesis") ;; this is where I write my phd thesis
(def BLOG-DIR "~/Documents/blog") ;; this is where I write my blog posts
(def CODE-DIR "~/Developer/") ;; almost all git repos are here

;; by default, we consider a file to have been edited 'recently' if it was
;; edited in the last 10 minutes. we also consider a git repo to have been
;; committed to 'recently' if it has had at least 4 commits in the last 60
;; minutes. We halve the commit threshold for git repositories found in the
;; code directory, because I have 'auto-commit' scripts running in blog and
;; thesis directories, but not in code directories.
(def EDIT-THRESHOLD 600000) ;; 10 minutes
(def COMMIT-THRESHOLD 60) ;; 60 minutes
(def AT-LEAST-N-COMMITS 4) ;; 4 commits


(defn has-extension?
  "Returns true if the file has an extension from a given list."
  [file extensions]
  (let [ext (-> file
                (str)
                (str/split #"\.")
                (last))]
    (some #(= ext %) extensions)))

(defn list-files
  "List all files in a directory, recursively.
   Optionally exclude files with some extensions."
  ([dir excluded-extensions]
   (filter #(not (has-extension? % excluded-extensions)) (list-files dir)))
  ([dir]
   (filter fs/regular-file? (fs/glob dir "**"))))

(defn time-since-modified
  "Return the number of milliseconds since a file was last modified."
  [path]
  (let [now (System/currentTimeMillis)]
    (- now (fs/file-time->millis (fs/last-modified-time path)))))

(defn edited-recently?
  "Return true if a file was edited 'recently' (default: 10 minutes ago)."
  ([path]
   (edited-recently? path 600000))
  ([path threshold]
   (< (time-since-modified path) threshold)))

(defn commits-in-last
  "Count the number of commits in a git repo in the last n minutes.
   Default n is 60 minutes."
  ([dir]
   (commits-in-last dir 60))
  ([dir n]
   (let [cmd (str "git -C " dir " log --since=\"" n " minutes ago\" --oneline")]
     (->> (shell {:out :string} cmd)
          :out
          (str/split-lines)
          (count)))))

(defn dir-is-hot?
  "Return true if a directory
   1. contains files that have been edited recently; and
   2. the git repo has had at least n commits in the last m minutes; and
   3. the git repo has had at least n/2 commits in the last m/2 minutes.
   
   Optionally exclude files with some extensions."
  ([dir n m excluded-extensions]
   (and (some edited-recently? (list-files dir excluded-extensions))
        (>= (commits-in-last dir (int (/ m 2))) (int (/ n 2)))
        (>= (commits-in-last dir m) n)))
  ([dir n m]
   (and (some edited-recently? (list-files dir))
        (>= (commits-in-last dir (int (/ m 2))) (int (/ n 2)))
        (>= (commits-in-last dir m) n))))

(defn is-git-repo?
  "Return true if a given dir path is a git repo.
   
   Checks for the presence of a .git directory in the given path.
   "
  [dir]
  (let [dir (fs/expand-home dir) ;; expand ~
        dir (fs/unixify dir)] ;; convert to unix path 
    (fs/exists? (str dir "/.git"))))


(defn list-git-repos
  "List all git repositories in a directory."
  ([dir]
   (let [subdirs (filter fs/directory? (fs/list-dir dir))]
     (if (empty? subdirs)
       []
       (filter #(is-git-repo? %) subdirs)))))

(defn -main
  "Detect whether we're 'on a roll'."
  [& args]
  (let [thesis? (dir-is-hot? (fs/unixify (fs/expand-home THESIS-DIR))
                             AT-LEAST-N-COMMITS COMMIT-THRESHOLD
                             ["yaml" "yml" "json" "pdf"])
        blog? (dir-is-hot?  (fs/unixify (fs/expand-home BLOG-DIR))
                            AT-LEAST-N-COMMITS COMMIT-THRESHOLD
                            ["yaml" "yml" "json" "bb" "py" "csv"])
        code-dirs (list-git-repos (fs/unixify (fs/expand-home CODE-DIR)))
        hot-code-dirs (filter #(dir-is-hot? (fs/unixify %)
                                            AT-LEAST-N-COMMITS
                                            (int (/ COMMIT-THRESHOLD 2)))
                              code-dirs)]
    (println (or thesis?
                ;;  blog?
                 (> (count hot-code-dirs) 0)))))

(-main *command-line-args*)
