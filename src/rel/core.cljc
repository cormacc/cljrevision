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

(defn increment-patch-version [{:keys [major minor patch prefix sep1 sep2 postfix]}]
  (let [new-patch (str (inc (Integer/parseInt patch)))]
    {:major major :minor minor :patch new-patch
     :prefix prefix :sep1 sep1 :sep2 sep2 :postfix postfix
     :new-version-string (str prefix major sep1 minor sep2 new-patch postfix)}))

(defn increment-minor-version [{:keys [major minor patch prefix sep1 sep2 postfix]}]
  (let [new-minor (str (inc (Integer/parseInt minor)))
        new-patch "0"]
    {:major major :minor new-minor :patch new-patch
     :prefix prefix :sep1 sep1 :sep2 sep2 :postfix postfix
     :new-version-string (str prefix major sep1 new-minor sep2 new-patch postfix)}))

(defn increment-major-version [{:keys [major minor patch prefix sep1 sep2 postfix]}]
  (let [new-major (str (inc (Integer/parseInt major)))
        new-minor "0"
        new-patch "0"]
    {:major new-major :minor new-minor :patch new-patch
     :prefix prefix :sep1 sep1 :sep2 sep2 :postfix postfix
     :new-version-string (str prefix new-major sep1 new-minor sep2 new-patch postfix)}))
