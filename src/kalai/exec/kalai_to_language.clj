(ns kalai.exec.kalai-to-language
  (:refer-clojure :exclude [compile])
  (:require [kalai.emit.langs :as l]
            [kalai.pass.kalai.pipeline :as kalai-pipeline]
            [kalai.pass.java.pipeline :as java-pipeline]
            [kalai.pass.rust.pipeline :as rust-pipeline]
            [clojure.tools.analyzer.jvm :as az]
            [clojure.tools.analyzer.jvm.utils :as azu]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [camel-snake-kebab.core :as csk])
  (:import (java.io File)
           (java.nio.file Paths)))

(def ext {::l/rust ".rs"
          ::l/cpp  ".cpp"
          ::l/java ".java"})

(def translators
  {::l/java java-pipeline/kalai->java
   ::l/rust rust-pipeline/kalai->rust})

(def file-naming-conventions
  {::l/java (fn java-file-naming [^String filename]
              (let [i (.lastIndexOf filename ".")]
                (assert (pos? i) "must have an extension")
                (str (csk/->PascalCase (subs filename 0 i))
                     (subs filename i))))
   ::l/rust (fn rust-file-naming [^String filename]
              (-> filename
                  (csk/->snake_case)
                  (str/lower-case)))})

(def package-naming-conventions
  {::l/java (fn [x] (-> x
                        (csk/->camelCase)
                        (str/lower-case)))
   ::l/rust (fn [x] (-> x
                        (csk/->snake_case)
                        (str/lower-case)))})

(defn analyze-forms [forms]
  (mapv az/analyze+eval forms))

(defn ns-url [file-path]
  (io/as-url (io/file file-path)))

(defn analyze-file [file-path]
  (with-redefs [azu/ns-url ns-url]
    (az/analyze-ns file-path)))

(defn read-kalai [file]
  (-> (analyze-file file)
      (kalai-pipeline/asts->kalai)))

(defn relative [^File base ^File file]
  (.getPath (.relativize (.toURI base) (.toURI file))))

(defn write-file [^String content ^String relative-path ^File transpile-dir lang]
  (let [file-naming (get file-naming-conventions lang)
        ;; "src" might be "test" sometimes, and might be language specific
        path (Paths/get (name lang) (into-array String ["src" relative-path]))
        target (-> (.getFileName path)
                   (str)
                   (file-naming)
                   (str/replace #"\.clj[csx]?$" (ext lang)))
        package-naming (get package-naming-conventions lang)
        package-name (-> (str (.getParent path))
                         (package-naming))
        output-file (io/file transpile-dir package-name target)]
    (.mkdirs (io/file (.getParent output-file)))
    (spit output-file content)))

(defn transpile-file [^File source-file {:keys [src-dir transpile-dir languages]}]
  (let [kalai (read-kalai source-file)
        relative-path (relative (io/file src-dir) source-file)]
    (doseq [[language translate] (select-keys translators languages)]
      (-> (translate kalai)
          (write-file relative-path transpile-dir language)))))

(defn transpile-all
  "options is a map of
  {:src-dir \"src/main/clj\"           ;; a directory containing Kalai source files that are inputs to transpilation>
   :transpile-dir \"src/main\"         ;; a the root directory for target language transpiled output>
   :languages #{:kalai.emit.lang/java} ;; the desired target languages
   }"
  ;; TODO: consider adding a spec to this
  [options]
  (doseq [^File source-file (file-seq (io/file (:src-dir options)))
          :when (not (.isDirectory source-file))]
    (when (:verbose options)
      (println "transpiling source file:" (str source-file)))
    (transpile-file source-file options)))
