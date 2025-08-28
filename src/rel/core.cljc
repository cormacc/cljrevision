(ns rel.core
  (:require [clojure.string :as str]))

;; From https://gist.github.com/blacktaxi/1676575
(defmacro f-string [^String string]
  (let [-re #"#\{(.*?)\}"
        fstr (clojure.string/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

(defn parse-revision [text revision-regex]
  (let [version-matcher (re-pattern revision-regex)
        version (re-find version-matcher text)
        [match prefix major sep1 minor sep2 patch postfix] version]
    {:major major :minor minor :patch patch
     :prefix prefix :sep1 sep1 :sep2 sep2 :postfix postfix
     :match match}))

(defn- build-version-string
  "Constructs the version string from components"
  [{:keys [prefix sep1 sep2 postfix]} major minor patch]
  (str prefix major sep1 minor sep2 patch postfix))

(defn increment-version
  "Increment version based on level.
  Returns the new version components and metadata."
  [{:keys [major minor patch prefix sep1 sep2 postfix]} level]
  (let [[new-major new-minor new-patch]
        (case level
          :major [(str (inc (Integer/parseInt major))) "0" "0"]
          :minor [major (str (inc (Integer/parseInt minor))) "0"]
          :patch [major minor (str (inc (Integer/parseInt patch)))])]
    {:major new-major
     :minor new-minor
     :patch new-patch
     :prefix prefix
     :sep1 sep1
     :sep2 sep2
     :postfix postfix
     :new-version-string (build-version-string
                          {:prefix prefix :sep1 sep1 :sep2 sep2 :postfix postfix}
                          new-major new-minor new-patch)}))
