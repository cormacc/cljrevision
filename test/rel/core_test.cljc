(ns rel.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [rel.core :as core]))

(deftest parse-revision-test
  (testing "parses version string with standard format"
    (let [text "(def current \"1.2.3\")"
          regex "(?<prefix>\\(def current \")(?<major>\\d+)(?<sep1>\\.)(?<minor>\\d+)(?<sep2>\\.)(?<patch>[\\w\\d]+)(?<postfix>\"\\))"
          result (core/parse-revision text regex)]
      (is (= "1" (:major result)))
      (is (= "2" (:minor result)))
      (is (= "3" (:patch result)))
      (is (= "(def current \"" (:prefix result)))
      (is (= "\")" (:postfix result)))
      (is (= "." (:sep1 result)))
      (is (= "." (:sep2 result)))
      (is (= "(def current \"1.2.3\")" (:match result)))))

  (testing "parses version with different separators"
    (let [text "version: 2-5-alpha"
          regex "(?<prefix>version: )(?<major>\\d+)(?<sep1>-)(?<minor>\\d+)(?<sep2>-)(?<patch>[\\w\\d]+)(?<postfix>)"
          result (core/parse-revision text regex)]
      (is (= "2" (:major result)))
      (is (= "5" (:minor result)))
      (is (= "alpha" (:patch result)))
      (is (= "-" (:sep1 result)))
      (is (= "-" (:sep2 result))))))

(deftest increment-patch-version-test
  (testing "increments patch version from 0"
    (let [revision {:major "1" :minor "2" :patch "0"
                    :prefix "(def current \"" :sep1 "." :sep2 "." :postfix "\")"}
          result (core/increment-patch-version revision)]
      (is (= "1" (:major result)))
      (is (= "2" (:minor result)))
      (is (= "1" (:patch result)))
      (is (= "(def current \"1.2.1\")" (:new-version-string result)))))

  (testing "increments patch version from 9 to 10"
    (let [revision {:major "1" :minor "2" :patch "9"
                    :prefix "(def current \"" :sep1 "." :sep2 "." :postfix "\")"}
          result (core/increment-patch-version revision)]
      (is (= "10" (:patch result)))
      (is (= "(def current \"1.2.10\")" (:new-version-string result)))))

  (testing "preserves major and minor versions"
    (let [revision {:major "5" :minor "7" :patch "3"
                    :prefix "v" :sep1 "." :sep2 "." :postfix ""}
          result (core/increment-patch-version revision)]
      (is (= "5" (:major result)))
      (is (= "7" (:minor result)))
      (is (= "4" (:patch result)))
      (is (= "v5.7.4" (:new-version-string result))))))

(deftest increment-minor-version-test
  (testing "increments minor version and resets patch to 0"
    (let [revision {:major "1" :minor "2" :patch "5"
                    :prefix "(def current \"" :sep1 "." :sep2 "." :postfix "\")"}
          result (core/increment-minor-version revision)]
      (is (= "1" (:major result)))
      (is (= "3" (:minor result)))
      (is (= "0" (:patch result)))
      (is (= "(def current \"1.3.0\")" (:new-version-string result)))))

  (testing "increments minor from 9 to 10"
    (let [revision {:major "2" :minor "9" :patch "3"
                    :prefix "v" :sep1 "." :sep2 "." :postfix ""}
          result (core/increment-minor-version revision)]
      (is (= "2" (:major result)))
      (is (= "10" (:minor result)))
      (is (= "0" (:patch result)))
      (is (= "v2.10.0" (:new-version-string result)))))

  (testing "resets patch even when it's already 0"
    (let [revision {:major "1" :minor "5" :patch "0"
                    :prefix "" :sep1 "." :sep2 "." :postfix ""}
          result (core/increment-minor-version revision)]
      (is (= "1" (:major result)))
      (is (= "6" (:minor result)))
      (is (= "0" (:patch result)))
      (is (= "1.6.0" (:new-version-string result))))))

(deftest increment-major-version-test
  (testing "increments major version and resets minor and patch to 0"
    (let [revision {:major "1" :minor "5" :patch "3"
                    :prefix "(def current \"" :sep1 "." :sep2 "." :postfix "\")"}
          result (core/increment-major-version revision)]
      (is (= "2" (:major result)))
      (is (= "0" (:minor result)))
      (is (= "0" (:patch result)))
      (is (= "(def current \"2.0.0\")" (:new-version-string result)))))

  (testing "increments major from 9 to 10"
    (let [revision {:major "9" :minor "12" :patch "45"
                    :prefix "version-" :sep1 "." :sep2 "." :postfix ""}
          result (core/increment-major-version revision)]
      (is (= "10" (:major result)))
      (is (= "0" (:minor result)))
      (is (= "0" (:patch result)))
      (is (= "version-10.0.0" (:new-version-string result)))))

  (testing "resets minor and patch even when already 0"
    (let [revision {:major "3" :minor "0" :patch "0"
                    :prefix "v" :sep1 "-" :sep2 "-" :postfix "-RELEASE"}
          result (core/increment-major-version revision)]
      (is (= "4" (:major result)))
      (is (= "0" (:minor result)))
      (is (= "0" (:patch result)))
      (is (= "v4-0-0-RELEASE" (:new-version-string result))))))

(deftest version-string-formats-test
  (testing "handles various version string formats correctly"
    (let [test-cases [{:input {:major "1" :minor "0" :patch "0"
                               :prefix "v" :sep1 "." :sep2 "." :postfix ""}
                       :expected-patch "v1.0.1"
                       :expected-minor "v1.1.0"
                       :expected-major "v2.0.0"}

                      {:input {:major "1" :minor "2" :patch "3"
                               :prefix "VERSION=" :sep1 "_" :sep2 "_" :postfix ";"}
                       :expected-patch "VERSION=1_2_4;"
                       :expected-minor "VERSION=1_3_0;"
                       :expected-major "VERSION=2_0_0;"}

                      {:input {:major "10" :minor "11" :patch "12"
                               :prefix "" :sep1 "." :sep2 "." :postfix "-SNAPSHOT"}
                       :expected-patch "10.11.13-SNAPSHOT"
                       :expected-minor "10.12.0-SNAPSHOT"
                       :expected-major "11.0.0-SNAPSHOT"}]]

      (doseq [{:keys [input expected-patch expected-minor expected-major]} test-cases]
        (testing (str "Format: " (:prefix input) "X" (:sep1 input) "Y" (:sep2 input) "Z" (:postfix input))
          (is (= expected-patch (:new-version-string (core/increment-patch-version input))))
          (is (= expected-minor (:new-version-string (core/increment-minor-version input))))
          (is (= expected-major (:new-version-string (core/increment-major-version input)))))))))

(deftest edge-cases-test
  (testing "handles large version numbers"
    (let [revision {:major "999" :minor "999" :patch "999"
                    :prefix "" :sep1 "." :sep2 "." :postfix ""}]
      (is (= "1000" (:patch (core/increment-patch-version revision))))
      (is (= "1000" (:minor (core/increment-minor-version revision))))
      (is (= "1000" (:major (core/increment-major-version revision))))))

  (testing "preserves all formatting elements"
    (let [revision {:major "1" :minor "2" :patch "3"
                    :prefix "###" :sep1 "~!~" :sep2 "@@@" :postfix "###"}
          patch-result (core/increment-patch-version revision)
          minor-result (core/increment-minor-version revision)
          major-result (core/increment-major-version revision)]
      (is (= "###1~!~2@@@4###" (:new-version-string patch-result)))
      (is (= "###1~!~3@@@0###" (:new-version-string minor-result)))
      (is (= "###2~!~0@@@0###" (:new-version-string major-result))))))