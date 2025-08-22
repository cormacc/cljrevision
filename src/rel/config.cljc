(ns rel.config
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [clj-yaml.core :as yaml]
            [rel.core :as core]
            [rel.changelog :as changelog]))

(def yaml-filename "releasables.yaml")

(defn find-yaml-root [dir]
  (let [candidate (fs/path dir yaml-filename)
        parent (fs/parent dir)]
    (if (fs/exists? candidate)
      dir
      (if (empty? parent)
        nil
        (recur parent)))))

(defn load-definition [file-path]
  (let [yaml-raw (slurp (str file-path))
        ;; For some reason I adopted a :key: convention with multiple colons....
        ;; Handle both ::key:: and :key: patterns
        yaml-data (-> yaml-raw
                      (str/replace #"::(\S+)::" "$1:")
                      (str/replace #":(\S+):" "$1:"))
        parsed (yaml/parse-string yaml-data)]
    ;; Convert releasables sequence to vector for easier access
    (if (and parsed (contains? parsed :releasables))
      (assoc parsed :releasables (vec (get parsed :releasables)))
      parsed)))

(defn load-all [path]
  (let [root (find-yaml-root path)
        definition (when root (load-definition (fs/path root yaml-filename)))]
    (when definition
      ;; Convert releasables sequence to vector for easier access
      (let [converted-def (if (contains? definition :releasables)
                            (assoc definition :releasables (vec (get definition :releasables)))
                            definition)]
        (assoc converted-def :root root)))))

(defn load-default [path]
  (let [all-data (load-all path)]
    (when all-data
      (let [releasables (get all-data :releasables)
            root (:root all-data)]
        (when (and root releasables (seq releasables))
          (assoc (first releasables) :root root))))))

(defn load-revision-info [{:keys [root revision] :as _releasable}]
  (when (and root revision)
    (let [file-path-rel (:src revision)
          file-path (fs/path root file-path-rel)]
      (when (fs/exists? file-path)
        (let [file-content (slurp (str file-path))
              rev (core/parse-revision file-content (:regex revision))
              changelog (changelog/extract file-content (:comment_prefix revision))
              entries (changelog/parse-entries changelog)]
          {:revision rev
           :changelog changelog
           :entries entries})))))
