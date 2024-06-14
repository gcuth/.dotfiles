#!/usr/bin/env bb
;;
;; A small script for interacting with Metaculus forecasts (via the API).
;;
;; All requests should use the Authorization HTTP header with a token, eg:
;; Authorization: Token 9944b09199c62bcf9418ad846dd0e4bbdfc6ee4b
;;
;; Usage: ./forecast.bb [predict|report|list|prompt] <options>
;;    eg: ./forecast.bb predict --id 123 --token 9944b09199... --value 0.59
;;            (should post a prediction of 0.59 on the question with id 123)
;;        ./forecast.bb check --id 123 --token 9944b09199...
;;            (should return current details of the question with id 123)
;;
;; The "prompt" command is a special case that allows for an interactive update
;; to forecasts. It first lists questions to find the binary question that is
;; open for a prediction and appropriate to predict on. It then prompts for a
;; new prediction value and posts it to the question.

(require '[babashka.cli :as cli]
         '[cheshire.core :as json]
         '[babashka.curl :as curl])

(def USERID "111805") ;; my user ID on Metaculus

(def DEFAULT-SEARCH-PARAMS
  [{"order_by" "last_prediction_time"
    "type" "forecast"
    "status" "open"
    "guessed_by" USERID
    "forecast_type" "binary"
    "limit" "100"}
   {"order_by" "-divergence"
    "type" "forecast"
    "status" "open"
    "guessed_by" USERID
    "forecast_type" "binary"
    "limit" "100"}
   {"order_by" "close_time"
    "type" "forecast"
    "status" "open"
    "not_guessed_by" USERID
    "forecast_type" "binary"
    "limit" "100"}])


(defn build-header
  "Build an HTTP header for Metaculus API requests."
  [token]
  {:headers {"Authorization" (str "Token " token)
             "Content-Type" "application/json"}})


(defn list-questions
  "List some questions on Metaculus."
  [& {:keys [token params]}]
  (cond (nil? token) (throw (ex-info "No token provided." {}))
        :else (let [url "https://www.metaculus.com/api2/questions/"
                    response (curl/get url
                                       (assoc (build-header token)
                                              :query-params params))]
                (:results (json/parse-string (:body response) true)))))


(defn get-question
  "Get details of a question from metaculus."
  [{:keys [id token]}]
  (cond (nil? id) (throw (ex-info "No Question ID provided." {}))
        (nil? token) (throw (ex-info "No token provided." {}))
        :else (let [url (str "https://www.metaculus.com/api2/questions/" id "/")
                    response (curl/get url (build-header token))]
                (json/parse-string (:body response) true))))


(defn get-question-type
  "Given a question object, return the type from [:possibilities :type]."
  [question]
  (get-in question [:possibilities :type]))

(defn is-binary?
  "Given a question object, return whether it is a binary question."
  [question]
  (= "binary" (get-question-type question)))

(defn is-continuous?
  "Given a question object, return whether it is a continuous question."
  [question]
  (= "continuous" (get-question-type question)))

(defn is-open?
  "Given a question object, return whether it is open for predictions."
  [question]
  (= "OPEN" (:active_state question)))


(defn post-binary-prediction
  "Post a binary prediction to Metaculus for a specific question."
  [{:keys [id token value]}]
  (let [url (str "https://www.metaculus.com/api2/questions/" id "/predict/")
        response (curl/post url
                            (assoc (build-header token)
                                   :body (json/generate-string {:prediction value})))]
    (json/parse-string (:body response) true)))

(defn post-continuous-prediction
  "Post a continuous prediction to Metaculus for a specific question."
  [{:keys [id token values]}]
  ;; 
  ;; The JSON schema continuous predictions is:
  ;; schema = { "type": "object",
  ;; "properties": {
  ;;   "kind": { "enum": (["logistic", "gaussian"]) },
  ;;   "avg": { "type": "number", "minimum": -2, "maximum": 3, },
  ;;   "stdev": { "type": "number", "minimum": 0.005, "maximum": 10, },
  ;;   "a": { "type": "number", "minimum": -0.96, "maximum": +0.96, },
  ;;   "low": { "type": "number", "minimum": 0.0099, "maximum": 1 - 0.0099, },
  ;;   "high": { "type": "number", "minimum": 0.0099, "maximum": 1 - 0.0099, },
  ;; },
  ;; "additionalProperties": boolean,
  ;; "required": ["avg", "stdev"] }
  ;;
  {:pre [(int? id) (string? token) (map? values)
         ;; Check that the values map contains the required keys ---
         (contains? values :kind)
         (contains? #{:logistic :gaussian} (:kind values))
         (or
          (and (= (:kind values) :logistic)
               (contains? values :avg)
               (<= -2 (:avg values) 3)
               (contains? values :stdev)
               (<= 0.005 (:stdev values) 10))
          (and (= (:kind values) :gaussian)
               (contains? values :x0)
               (contains? values :s)))
         (contains? values :a)
         (<= -0.96 (:a values) 0.96)
         (contains? values :low)
         (<= 0.0099 (:low values) (- 1 0.0099))
         (contains? values :high)
         (<= 0.0099 (:high values) (- 1 0.0099))]}
  (let [url (str "https://www.metaculus.com/api2/questions/" id "/predict/")
        response (curl/post url
                            (assoc (build-header token)
                                   :body (json/generate-string values)))]
    (json/parse-string (:body response) true)))


(defn post-prediction
  "Post a prediction to Metaculus for a specific question."
  [{:keys [id token value type]}]
  (cond (nil? id) (throw (ex-info "No Question ID provided." {}))
        (nil? token) (throw (ex-info "No token provided." {}))
        (nil? value) (throw (ex-info "No prediction value provided." {}))
        (nil? type) (throw (ex-info "No prediction type provided." {}))
        (= type "binary") (post-binary-prediction {:id id :token token :value value})
        (= type "continuous") (post-continuous-prediction {:id id :token token :values value})
        :else (throw (ex-info "Unknown prediction type." {}))))


(defn get-current-community-prediction
  "Get the current community prediction from a question."
  [question]
  (cond (is-binary? question)
        (-> question
            :community_prediction
            :history
            last
            :x2
            :weighted_avg)
        (is-continuous? question)
        (-> question
            :community_prediction
            :history
            last)
        :else nil))


(defn get-current-user-prediction
  "Get the current user prediction from a question."
  [question]
  (-> question
      :my_predictions
      :predictions
      last
      :x))


(defn prompt-for-prediction
  "Find an appropriate question for the user to predict on, and then prompt for
   a new prediction."
  [{:keys [token]}]
  (let [params DEFAULT-SEARCH-PARAMS
        ;; get a list of questions for each of the search params maps
        questions (if (map? params)
                    (list-questions {:token token
                                     :params params})
                    (apply concat (map #(list-questions {:token token
                                                         :params %})
                                       params)))
        ;; remove questions with a 'group' value
        ;; and questions without a community prediction
        questions (->> questions
                       (filter #(nil? (:group %)))
                       (remove #(nil? (get-current-community-prediction %)))
                       (filter #(is-open? %))
                       (filter #(is-binary? %)))]
    (println "Found" (count questions) "questions.")
    (doseq [question questions]
      (let [id (:id question)
            title (:title question)
            current-community (get-current-community-prediction question)]
        (println (str "[" id "] " title " [" current-community "]: "))
        (post-prediction
         {:id id
          :value (Float/parseFloat (read-line))
          :token token
          :type "binary"})))))


(def cli-spec
  {:id {:default nil
        :description "The ID of the forecast to interact with."
        :parse-fn int}
   :username {:default nil
              :description "The username to use for authentication."
              :parse-fn str}
   :token {:default nil
           :description "The API token to use for authentication."
           :parse-fn str}
   :json {:default false
          :description "Whether to output JSON when reporting on questions."
          :parse-fn boolean}})

(defn -main []
  (let [input (cli/parse-args *command-line-args* cli-spec)
        command (first (:args input))
        opts (:opts input)]
    (cond (= command "report")
          (let [question (get-question opts)]
            (if (:json opts)
              (println (json/generate-string question))
              (println question)))

          (= command "predict")
          (let [prediction (post-prediction opts)] ;; TODO: Check for question type and post accordingly
            (println prediction))

          (= command "list")
          (let [questions (list-questions (assoc opts :params (rand-nth DEFAULT-SEARCH-PARAMS)))]
            (if (:json opts)
              (println (json/generate-string questions))
              (doseq [q questions]
                (println (str (:id q) ": " (:title q))))))

          (= command "prompt")
          (-> (prompt-for-prediction opts))

          (= command "adopt")
          (if (:id opts)
            (let [question (get-question opts)
                  community (get-current-community-prediction question)
                  user (get-current-user-prediction question)]
              (cond (nil? community) (println "No community prediction available.")
                    (= community user) (println "Community and user predictions already match.")
                    (not (is-open? question)) (println "Question is not open for predictions.")
                    (not (is-binary? question)) (println "Question type is not yet supported.")
                    :else (println (post-prediction (assoc opts :value community :type "binary")))))
            (let [questions (->> (apply concat (map #(list-questions {:token (:token opts)
                                                                      :params %})
                                                    DEFAULT-SEARCH-PARAMS))
                                ;;  (filter #(nil? (:group %)))
                                 (remove #(nil? (get-current-community-prediction %)))
                                 (filter #(is-open? %))
                                 (filter #(is-binary? %)))]
              (doseq [question questions]
                (let [community (get-current-community-prediction question)
                      user (get-current-user-prediction question)]
                  (if (not= community user)
                    (do
                      (println (str (:title question) " (" user "->" community ")"))
                      (println (post-prediction (assoc opts
                                                       :id (:id question)
                                                       :value community
                                                       :type "binary")))
                      (println ""))
                    (println (str "Community and user predictions already match for \"" (:title question) "\"")))))))
          
          (= command "check")
          (let [question (get-question opts)]
            (println (json/generate-string (get-current-community-prediction question))))

          :else (println "Unknown command."))))

(-main)