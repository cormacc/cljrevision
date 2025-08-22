(ns rel.config-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [clj-yaml.core :as yaml]
            [rel.core :as core]
            [rel.config :as config]
            [rel.changelog :as changelog]))

;; Now using actual implementations from rel.config and rel.changelog namespaces

;; === TEST FIXTURES ===

(def yaml-configs
  {:minimal ":releasables:\n  - :id: test"

   :standard ":releasables:
  - :id: revision
    :revision:
      :src: src/version.cljc
      :regex: '(?<prefix>\\(def current \")(?<major>\\d+)(?<sep1>\\.)(?<minor>\\d+)(?<sep2>\\.)(?<patch>[\\w\\d]+)(?<postfix>\"\\))'
      :comment_prefix: ';;'
    :build_steps:
      - echo test
    :artefacts:
      - :src: test.txt"

   :invalid ":releasables:\n  - :id: test\n    :invalid_yaml: [\n"

   :colon_format "::releasables::\n  - ::id:: test\n    ::revision::\n      ::src:: src/version.cljc"})

(def version-files
  {:standard "(def current \"1.2.3\")

;; <BEGIN CHANGELOG>
;;
;; Version 1.2.3 (1 Jan 2025)
;; - Initial version
;; - Added basic functionality
;;
;; Version 1.0.0 (15 Dec 2024)
;; - First release
;; - Basic features
;;
;; <END CHANGELOG>"

   :no_changelog "(def current \"1.0.0\")"

   :malformed_version "(def current \"not-a-version\")"

   :missing_markers "(def current \"1.0.0\")

;; Version 1.0.0
;; - No markers"

   :complex_format "version-1_2_3-SNAPSHOT

# <BEGIN CHANGELOG>
#
# Version 1.2.3-SNAPSHOT (1 Jan 2025)
# - Complex version format
# - Different comment prefix
#
# <END CHANGELOG>"})

;; === HELPER FUNCTIONS ===

(defn create-temp-dir []
  (let [temp-dir (fs/create-temp-dir {:prefix "config-test-"})]
    (str temp-dir)))

(defn create-temp-file [content]
  (let [temp-file (fs/create-temp-file {:prefix "config-test-" :suffix ".yaml"})]
    (spit (str temp-file) content)
    (str temp-file)))

;; === PHASE 1: PURE FUNCTIONS ===

(deftest extract-changelog-test
  (testing "extracts changelog with standard comment prefix"
    (let [text (:standard version-files)
          result (changelog/extract text ";;")]
      (is (not (nil? result)))
      (is (str/includes? result "Version 1.2.3"))
      (is (str/includes? result "Initial version"))
      (is (not (str/includes? result ";;")))))

  (testing "handles different comment prefixes"
    (let [text (:complex_format version-files)
          result (changelog/extract text "#")]
      (is (not (nil? result)))
      (is (str/includes? result "Version 1.2.3-SNAPSHOT"))
      (is (str/includes? result "Complex version format"))
      (is (not (str/includes? result "#")))))

  (testing "handles missing changelog markers"
    (let [text (:missing_markers version-files)
          result (changelog/extract text ";;")]
      (is (nil? result))))

  (testing "handles empty text"
    (let [result (changelog/extract "" ";;")]
      (is (nil? result))))

  (testing "handles text without changelog"
    (let [text (:no_changelog version-files)
          result (changelog/extract text ";;")]
      (is (nil? result))))

  (testing "strips comment prefixes correctly"
    (let [text ";; <BEGIN CHANGELOG>
;;
;; Version 1.0.0 (1 Jan 2025)
;; - Test entry
;; - Another test
;;
;; <END CHANGELOG>"
          result (changelog/extract text ";;")]
      (is (not (str/includes? result ";;")))
      (is (str/includes? result "Version 1.0.0"))
      (is (str/includes? result "Test entry")))))

(deftest parse-entries-test
  (testing "parses valid changelog entries"
    (let [changelog "
Version 1.2.3 (1 Jan 2025)
- Initial version
- Added basic functionality

Version 1.0.0 (15 Dec 2024)
- First release
- Basic features
"
          result (changelog/parse-entries changelog)]
      (is (= 2 (count result)))
      (is (= "1.2.3" (:version (first result))))
      (is (= "1 Jan 2025" (:date (first result))))
      (is (= ["Initial version" "Added basic functionality"] (:changes (first result))))
      (is (= "1.0.0" (:version (second result))))
      (is (= ["First release" "Basic features"] (:changes (second result))))))

  (testing "handles single entry"
    (let [changelog "
Version 1.0.0 (1 Jan 2025)
- Single entry
"
          result (changelog/parse-entries changelog)]
      (is (= 1 (count result)))
      (is (= "1.0.0" (:version (first result))))
      (is (= ["Single entry"] (:changes (first result))))))

  (testing "handles empty changelog"
    (let [result (changelog/parse-entries "")]
      (is (= [] result))))

  (testing "handles malformed entries"
    (let [changelog "Not a proper changelog entry"
          result (changelog/parse-entries changelog)]
      (is (= [] result))))

  (testing "handles different date formats"
    (let [changelog "
Version 2.0.0 (Dec 15, 2024)
- Different date format

Version 1.5.0 (2024-12-01)
- ISO date format
"
          result (changelog/parse-entries changelog)]
      (is (= 2 (count result)))
      (is (= "Dec 15, 2024" (:date (first result))))
      (is (= "2024-12-01" (:date (second result))))))

  (testing "preserves full text match"
    (let [changelog "
Version 1.0.0 (1 Jan 2025)
- Test entry
"
          result (changelog/parse-entries changelog)]
      (is (str/includes? (:full-text (first result)) "Version 1.0.0"))
      (is (str/includes? (:full-text (first result)) "Test entry")))))

;; === PHASE 2: FILESYSTEM OPERATIONS ===

(deftest find-yaml-root-test
  (testing "finds config in current directory"
    (let [temp-dir (create-temp-dir)
          yaml-path (fs/path temp-dir config/yaml-filename)]
      (spit (str yaml-path) (:minimal yaml-configs))
      (try
        (let [result (config/find-yaml-root temp-dir)]
          (is (= temp-dir result)))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "finds config in parent directory"
    (let [temp-root (create-temp-dir)
          temp-subdir (fs/path temp-root "subdir")
          yaml-path (fs/path temp-root config/yaml-filename)]
      (fs/create-dir temp-subdir)
      (spit (str yaml-path) (:minimal yaml-configs))
      (try
        (let [result (config/find-yaml-root (str temp-subdir))]
          (is (= temp-root (str result))))
        (finally
          (fs/delete-tree temp-root)))))

  (testing "finds config in nested parent directory"
    (let [temp-root (create-temp-dir)
          nested-dir (fs/path temp-root "a" "b" "c")
          yaml-path (fs/path temp-root config/yaml-filename)]
      (fs/create-dirs nested-dir)
      (spit (str yaml-path) (:minimal yaml-configs))
      (try
        (let [result (config/find-yaml-root (str nested-dir))]
          (is (= temp-root (str result))))
        (finally
          (fs/delete-tree temp-root)))))

  (testing "returns nil when not found"
    (let [temp-dir (create-temp-dir)]
      (try
        (let [result (config/find-yaml-root temp-dir)]
          (is (nil? result)))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "handles non-existent directory"
    (let [non-existent "/path/that/does/not/exist"]
      (let [result (config/find-yaml-root non-existent)]
        (is (nil? result))))))

;; === PHASE 3: YAML PROCESSING ===

(deftest load-definition-test
  (testing "loads valid YAML"
    (let [yaml-file (create-temp-file (:standard yaml-configs))]
      (try
        (let [result (config/load-definition yaml-file)]
          (is (map? result))
          (is (contains? result :releasables))
          (is (sequential? (get result :releasables)))
          (is (= "revision" (get-in result [:releasables 0 :id]))))
        (finally
          (fs/delete yaml-file)))))

  (testing "handles colon replacement correctly"
    (let [yaml-file (create-temp-file (:colon_format yaml-configs))]
      (try
        (let [result (config/load-definition yaml-file)]
          (is (map? result))
          ;; After colon replacement, should have normal keywords
          (is (contains? result :releasables))
          (is (= "test" (get-in result [:releasables 0 :id]))))
        (finally
          (fs/delete yaml-file)))))

  (testing "loads minimal YAML"
    (let [yaml-file (create-temp-file (:minimal yaml-configs))]
      (try
        (let [result (config/load-definition yaml-file)]
          (is (map? result))
          (is (= "test" (get-in result [:releasables 0 :id]))))
        (finally
          (fs/delete yaml-file)))))

  (testing "throws on invalid YAML"
    (let [yaml-file (create-temp-file (:invalid yaml-configs))]
      (try
        (is (thrown? Exception (config/load-definition yaml-file)))
        (finally
          (fs/delete yaml-file)))))

  (testing "handles empty file"
    (let [yaml-file (create-temp-file "")]
      (try
        (let [result (config/load-definition yaml-file)]
          (is (or (nil? result) (= {} result))))
        (finally
          (fs/delete yaml-file)))))

  (testing "throws on file not found"
    (is (thrown? Exception (config/load-definition "/non/existent/file.yaml")))))

;; === PHASE 4: INTEGRATION FUNCTIONS ===

(deftest load-all-test
  (testing "loads config from current directory"
    (let [temp-dir (create-temp-dir)
          yaml-path (fs/path temp-dir config/yaml-filename)]
      (spit (str yaml-path) (:standard yaml-configs))
      (try
        (let [result (config/load-all temp-dir)]
          (is (map? result))
          (is (= temp-dir (str (:root result))))
          (is (contains? result :releasables))
          (is (= "revision" (get-in result [:releasables 0 :id]))))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "loads config from parent directory"
    (let [temp-root (create-temp-dir)
          temp-subdir (fs/path temp-root "subdir")
          yaml-path (fs/path temp-root config/yaml-filename)]
      (fs/create-dir temp-subdir)
      (spit (str yaml-path) (:minimal yaml-configs))
      (try
        (let [result (config/load-all (str temp-subdir))]
          (is (map? result))
          (is (= temp-root (str (:root result))))
          (is (= "test" (get-in result [:releasables 0 :id]))))
        (finally
          (fs/delete-tree temp-root)))))

  (testing "returns nil when no config found"
    (let [temp-dir (create-temp-dir)]
      (try
        (let [result (config/load-all temp-dir)]
          (is (nil? result)))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "integrates find-yaml-root and load-definition correctly"
    (let [temp-root (create-temp-dir)
          nested-dir (fs/path temp-root "a" "b")
          yaml-path (fs/path temp-root config/yaml-filename)]
      (fs/create-dirs nested-dir)
      (spit (str yaml-path) (:colon_format yaml-configs))
      (try
        (let [result (config/load-all (str nested-dir))]
          (is (= temp-root (str (:root result))))
          (is (= "test" (get-in result [:releasables 0 :id]))))
        (finally
          (fs/delete-tree temp-root))))))

(deftest load-default-test
  (testing "returns first releasable with root"
    (let [temp-dir (create-temp-dir)
          yaml-path (fs/path temp-dir config/yaml-filename)]
      (spit (str yaml-path) (:standard yaml-configs))
      (try
        (let [result (config/load-default temp-dir)]
          (is (map? result))
          (is (= temp-dir (str (:root result))))
          (is (= "revision" (:id result)))
          (is (contains? result :revision))
          (is (contains? result :build_steps)))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "handles empty releasables list"
    (let [temp-dir (create-temp-dir)
          yaml-path (fs/path temp-dir config/yaml-filename)]
      (spit (str yaml-path) ":releasables: []")
      (try
        (let [result (config/load-default temp-dir)]
          (is (nil? result)))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "handles missing releasables key"
    (let [temp-dir (create-temp-dir)
          yaml-path (fs/path temp-dir config/yaml-filename)]
      (spit (str yaml-path) ":other_key: value")
      (try
        (let [result (config/load-default temp-dir)]
          (is (nil? result)))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "returns nil when no config found"
    (let [temp-dir (create-temp-dir)]
      (try
        (let [result (config/load-default temp-dir)]
          (is (nil? result)))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "preserves all releasable properties"
    (let [temp-dir (create-temp-dir)
          yaml-path (fs/path temp-dir config/yaml-filename)]
      (spit (str yaml-path) ":releasables:
  - :id: complex
    :revision:
      :src: version.clj
    :build_steps:
      - step1
      - step2
    :artefacts:
      - :src: file1
      - :src: file2")
      (try
        (let [result (config/load-default temp-dir)]
          (is (= "complex" (:id result)))
          (is (= "version.clj" (get-in result [:revision :src])))
          (is (= ["step1" "step2"] (:build_steps result)))
          (is (= 2 (count (:artefacts result)))))
        (finally
          (fs/delete-tree temp-dir))))))

(deftest load-revision-info-test
  (testing "loads revision info from valid file"
    (let [temp-dir (create-temp-dir)
          version-path (fs/path temp-dir "version.cljc")
          releasable {:root temp-dir
                      :revision {:src "version.cljc"
                                 :regex "(?<prefix>\\(def current \")(?<major>\\d+)(?<sep1>\\.)(?<minor>\\d+)(?<sep2>\\.)(?<patch>[\\w\\d]+)(?<postfix>\"\\))"
                                 :comment_prefix ";;"}}]
      (spit (str version-path) (:standard version-files))
      (try
        (let [result (config/load-revision-info releasable)]
          (is (map? result))
          (is (contains? result :revision))
          (is (contains? result :changelog))
          (is (contains? result :entries))
          (is (= "1" (get-in result [:revision :major])))
          (is (= "2" (get-in result [:revision :minor])))
          (is (= "3" (get-in result [:revision :patch])))
          (is (= 2 (count (:entries result))))
          (is (= "1.2.3" (get-in result [:entries 0 :version]))))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "handles missing source file"
    (let [temp-dir (create-temp-dir)
          releasable {:root temp-dir
                      :revision {:src "nonexistent.cljc"
                                 :regex "test"
                                 :comment_prefix ";;"}}]
      (try
        (let [result (config/load-revision-info releasable)]
          (is (nil? result)))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "handles missing changelog section"
    (let [temp-dir (create-temp-dir)
          version-path (fs/path temp-dir "version.cljc")
          releasable {:root temp-dir
                      :revision {:src "version.cljc"
                                 :regex "(?<prefix>\\(def current \")(?<major>\\d+)(?<sep1>\\.)(?<minor>\\d+)(?<sep2>\\.)(?<patch>[\\w\\d]+)(?<postfix>\"\\))"
                                 :comment_prefix ";;"}}]
      (spit (str version-path) (:no_changelog version-files))
      (try
        (let [result (config/load-revision-info releasable)]
          (is (map? result))
          (is (= "1" (get-in result [:revision :major])))
          (is (= "0" (get-in result [:revision :minor])))
          (is (= "0" (get-in result [:revision :patch])))
          (is (nil? (:changelog result)))
          (is (or (= [] (:entries result)) (nil? (:entries result)))))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "handles malformed version file"
    (let [temp-dir (create-temp-dir)
          version-path (fs/path temp-dir "version.cljc")
          releasable {:root temp-dir
                      :revision {:src "version.cljc"
                                 :regex "(?<prefix>\\(def current \")(?<major>\\d+)(?<sep1>\\.)(?<minor>\\d+)(?<sep2>\\.)(?<patch>[\\w\\d]+)(?<postfix>\"\\))"
                                 :comment_prefix ";;"}}]
      (spit (str version-path) (:malformed_version version-files))
      (try
        (let [result (config/load-revision-info releasable)]
          (is (map? result))
          ;; Should still return structure but with nil revision
          (is (or (nil? (:revision result))
                  (some nil? (vals (select-keys (:revision result) [:major :minor :patch]))))))
        (finally
          (fs/delete-tree temp-dir)))))

  (testing "handles missing root or revision config"
    (let [releasable-no-root {:revision {:src "test"}}
          releasable-no-revision {:root "/tmp"}]
      (is (nil? (config/load-revision-info releasable-no-root)))
      (is (nil? (config/load-revision-info releasable-no-revision)))))

  (testing "integrates all sub-functions correctly"
    (let [temp-dir (create-temp-dir)
          version-path (fs/path temp-dir "version.cljc")
          releasable {:root temp-dir
                      :revision {:src "version.cljc"
                                 :regex "(?<prefix>\\(def current \")(?<major>\\d+)(?<sep1>\\.)(?<minor>\\d+)(?<sep2>\\.)(?<patch>[\\w\\d]+)(?<postfix>\"\\))"
                                 :comment_prefix ";;"}}]
      (spit (str version-path) (:standard version-files))
      (try
        (let [result (config/load-revision-info releasable)]
          ;; Test that parse-revision worked
          (is (= "1.2.3" (str (get-in result [:revision :major]) "."
                              (get-in result [:revision :minor]) "."
                              (get-in result [:revision :patch]))))
          ;; Test that extract-changelog worked
          (is (str/includes? (:changelog result) "Version 1.2.3"))
          ;; Test that parse-entries worked
          (is (= 2 (count (:entries result))))
          (is (= ["Initial version" "Added basic functionality"]
                 (get-in result [:entries 0 :changes]))))
        (finally
          (fs/delete-tree temp-dir))))))
