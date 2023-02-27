#!/usr/bin/env bb
;
; A script for reporting metaculus questions.

(require '[clojure.tools.cli :refer [parse-opts]]
         '[cheshire.core :as json]
         '[babashka.fs :as fs]
         '[clojure.walk :as walk]
         '[org.httpkit.client :as http])

(defn login
  "Login and return the basic cookie details."
  [& {:keys [username password subdomain]}]
  (let [url (str "https://" subdomain ".metaculus.com/api2/accounts/login/")
        response (http/post url
                            {:form-params {:username username
                                           :password password}})
        cookie (-> response deref :headers :set-cookie)]
    {:csrf (-> (re-find #"csrftoken=([^;]+)" cookie) second)
     :session (-> (re-find #"sessionid=([^;]+)" cookie) second)}))

(defn build-cookie
  "Take auth details (returned from a login) and build cookie string."
  [auth-details]
  (let [csrf (get auth-details :csrf)
        session (get auth-details :session)]
    (str "csrftoken=" csrf "; sessionid=" session)))

(defn get-question
  "Get a question from metaculus."
  [& {:keys [username password question-id subdomain]}]
  (let [url (str "https://" subdomain ".metaculus.com/api2/questions/" question-id "/")
        cookie (build-cookie (login {:username username
                                     :password password
                                     :subdomain subdomain}))]
    (-> (http/get url {:headers {"Cookie" cookie}})
        deref
        :body
        (json/parse-string true))))

(defn open?
  "Take a question and check whether it's open for predictions."
  [question]
  (get-in question [:user_perms :PREDICT]))

(defn current-user-p
  "Get the current user prediction (if any) from a question."
  [question]
  (->> question
       :my_predictions
       :predictions
       (sort-by :t)
       last))

(defn current-community-p
  "Get the latest community prediction from a question."
  [question]
  (->> question
       :community_prediction
       :history
       (sort-by :np)
       last))

(defn clamp-binary
  "Clamp a binary prediction to the appropriate range & precision."
  [prediction]
  (let [round (fn [x] (if (or (> x 0.99) (< x 0.01))
                        (float (/ (int (* x 1000)) 1000))
                        (float (/ (int (* x 100)) 100))))]
    (->> prediction
         (min 0.999)
         (max 0.001)
         (round))))

(defn post-binary-prediction
  "Post a prediction to a binary question."
  [& {:keys [username password question-id binary-prediction subdomain]}]
  (let [api-url (str "https://" subdomain ".metaculus.com/api2/")
        url (str api-url "questions/" question-id "/predict/")
        auth-details (login {:username username
                             :password password
                             :subdomain subdomain})
        cookie (build-cookie auth-details)]
    (-> (http/post url
                   {:form-params {:prediction (clamp-binary binary-prediction)
                                  :void false}
                    :headers {"Cookie" cookie
                              "X-CSRFToken" (:csrf auth-details)
                              "Referer" api-url}})
        deref)))


(defn encode-url [base-url params]
  (let [encoded-params (->> params
                            (walk/stringify-keys)
                             (map (fn [[k v]] (str/join "=" [k v])))
                             (str/join "&"))]
    (format (str base-url "?%s") encoded-params)))

(encode-url "https://spae.clj/questions/" {:a 1 :b 2})

(defn list-open-question-ids
  "List the ids of all open questions."
  [& {:keys [username password subdomain limit offset results maximum]}]
  (let [api-url (str "https://" subdomain ".metaculus.com/api2/")
        url (str api-url "questions/")
        ; encode the query string
        url (encode-url url {:status "open"
                             :type "forecast"
                             :limit (or limit 100)
                             :offset (or offset 0)
                             :order_by "-activity"})
        cookie (build-cookie (login {:username username
                                     :password password
                                     :subdomain subdomain}))
        response (-> (http/get url {:headers {"Cookie" cookie}})
                     deref
                     :body
                     (json/parse-string true))]
    (if (and (:next response) (< (count (or results [])) (or maximum 200)))
      (list-open-question-ids {:username username
                               :password password
                               :subdomain subdomain
                               :limit (or limit 100)
                               :offset (+ (or offset 0) (or limit 100))
                               :maximum maximum
                               :results (concat (or results [])
                                                (into [] (map :id
                                                              (:results response))))})
      results)))

; (defn post-range-prediction
;   "Post a prediction to a range question."
;   [& {:keys [username password question-id range-prediction subdomain]}]
;   (let [api-url (str "https://" subdomain ".metaculus.com/api2/")
;         url (str api-url "questions/" question-id "/predict/")
;         auth-details (login {:username username
;                              :password password
;                              :subdomain subdomain})
;         cookie (build-cookie auth-details)]
;     (-> (http/post url
;                    {:body (json/generate-string {:prediction range-prediction
;                                   :void false})
;                     :headers {"Cookie" cookie
;                               "X-CSRFToken" (:csrf auth-details)
;                               "Referer" api-url}})
;         deref)
;   ) 
;   )

; ; TEST THE GET QUESTION FLOW
; (def BINARYID 6197)
; (def RANGEID 4615)

; (def BINARYQ (get-question {:question-id BINARYID :username "galen" :password "6s5Y@6oo#4kkA^cX55l4" :subdomain "www"}))
; (def RANGEQ (get-question {:question-id RANGEID :username "galen" :password "6s5Y@6oo#4kkA^cX55l4" :subdomain "www"}))

; (:community_prediction RANGEQ)
; ; TEST - NOT WORKING!
; (post-range-prediction {:question-id RANGEID
;                         :username "galen"
;                          :password "6s5Y@6oo#4kkA^cX55l4"
;                          :subdomain "www"
;                          :range-prediction {:kind "multi"
;                                             :d [{:kind "logistic"
;                                                  :x0 0.3925
;                                                  :s 0.0626
;                                                  :a 0.1079
;                                                  :w 1
;                                                  :low 0.01
;                                                  :high 0.99}
;                                                 ]
;                                             }
;                          })




(def cli-defaults
  "Default options (from file!) for the command line interface."
  (json/parse-string
    (->> ["~/.spae.json" "~/.config/spae.json"]
         (map #(fs/expand-home %))
         (filter fs/exists?)
         (map str)
         (first)
         (slurp))
    true))

(def cli-opts
  [["-u" "--username Username" "Metaculus Username"
    :default (get-in cli-defaults [:metaculus :username])]
   ["-p" "--password Password" "Metaculus Password"
    :default (get-in cli-defaults [:metaculus :password])]
   ["-q" "--question-id ID" "Metaculus Question ID"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a positive integer"]]
   ["-b" "--binary-prediction Prediction" "Binary Prediction"
    :validate [#(>= 0.99 % 0.01) "Must be a float between 0.01 and 0.99"]
    :parse-fn #(Float/parseFloat %)]
   ["-s" "--subdomain Metaculus Domain"
    "The domain of the metaculus instance to use"
    :default (get-in cli-defaults [:metaculus :subdomain] "www")]])

(defn -main [& args]
  (let [{:keys [arguments options errors]} (parse-opts (first args) cli-opts)]
    (cond (= "report" (first arguments))
            (-> options
                prn
                )
          (= "predict" (first arguments)) (prn "Predicting")
          (= "list" (first arguments)) (prn (list-open-question-ids options))
          :else (do (println "Arguments: " arguments)
                    (println "Options: " options)
                    (println "Errors: " errors)))))

(-main *command-line-args*)
