(ns rel.changelog
  (:require [clojure.string :as str]
            [rel.core :as core]))

(def changelog-matcher #"(?s)<BEGIN CHANGELOG>(.+)<END CHANGELOG>")

(def entry-matcher #"(?m)^Version (\S+) \((.+)\)\n((?:- .+\n)+)")

(defn extract [text comment-prefix]
  (let [prefix-matcher (re-pattern (str "(?m)^" comment-prefix " ?"))
        match (re-find changelog-matcher text)]
    (when match
      (-> match
          second
          (str/replace prefix-matcher "")))))

(defn parse-entries [changelog-text]
  (when changelog-text
    (->> (re-seq entry-matcher changelog-text)
         (mapv (fn [[match version date changes]]
                 {:version version
                  :date date
                  :changes (-> (str/replace changes #"(?m)^- " "")
                               (str/split #"\n")
                               (->> (remove str/blank?)))
                  :full-text match})))))

(defn format-entry [version date changelog-lines comment-prefix]
  (let [header (core/f-string "#{comment-prefix} Version #{version} (#{date})")
        change-lines (map #(str comment-prefix " - " %) changelog-lines)
        blank-line (str comment-prefix)]
    (str/join "\n" (concat [blank-line header] change-lines))))
