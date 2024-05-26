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


(defn post-prediction
  "Post a prediction to Metaculus for a specific question.
   
   (Only supports binary questions for now.)
   "
  [{:keys [id token value]}]
  (let [url (str "https://www.metaculus.com/api2/questions/" id "/predict/")
        response (curl/post url
                            (assoc (build-header token)
                                   :body (json/generate-string {:prediction value})))]
    (json/parse-string (:body response) true)))


(defn get-current-community-prediction
  "Get the current community prediction from a question."
  [question]
  (-> question
      :community_prediction
      :history
      last
      :x2
      :weighted_avg))

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
                       (remove #(nil? (get-current-community-prediction %))))
        question (rand-nth questions)
        id (:id question)
        title (:title question)
        current-community (get-current-community-prediction question)]
    (println (str "[" id "] " title " [" current-community "]: "))
    {:id id
     :value (Float/parseFloat (read-line))}))


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
          (let [prediction (post-prediction opts)]
            (println prediction))

          (= command "list")
          (let [questions (list-questions (assoc opts :params (rand-nth DEFAULT-SEARCH-PARAMS)))]
            (if (:json opts)
              (println (json/generate-string questions))
              (doseq [q questions]
                (println (str (:id q) ": " (:title q))))))

          (= command "prompt")
          (-> (prompt-for-prediction opts)
              (assoc :token (:token opts))
              (post-prediction)
              (println))
          
          :else (println "Unknown command."))))

(-main)