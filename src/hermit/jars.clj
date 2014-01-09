(ns hermit.jars
  (:require [clojure.string :as str])
  (:import [java.util.zip ZipInputStream]
           [java.net URL URI]))

(defn url-of-jar-file
  "jar:file:/Some/file.jar!/path/to/resource
   => file:/Some/file.jar"
  [jar-url]
  (.getJarFileURL (.openConnection jar-url)))

(defn in-jar-path
  "jar:file:/Some/file.jar!/path/to/resource
   => /path/to/resource"
  [url]
  (second (str/split (.getPath url) #"!")))

(defn parent-jar-url
  "jar:file:/Some/file.jar!/path/to/resource
   => jar:file:/Some/file.jar!/path/to/"
  [url]
  (let [jar-uri       (url-of-jar-file url)
        in-jar-path   (in-jar-path url)
        in-jar-parent (.resolve (URI. in-jar-path) ".")]
    (URL. (str "jar:" jar-uri "!" in-jar-parent))))

(defn list-jar-resources
  "jar:file:/Some/file.jar!/path/to/resource
   => (path/to/resource/file1.sh, path/to/resource/file2.sh)"
  [url]
  (let [zip            (ZipInputStream. (.openStream (url-of-jar-file url)))
        prefix         (str/replace-first (in-jar-path url) #"^/" "")
        prefix-pattern (str/re-quote-replacement prefix)]
    (map #(str/replace-first % prefix-pattern "")
         (filter #(.startsWith % prefix)
                 (map #(.getName %)
                      (filter #(not (.isDirectory %))
                              (take-while #(not (nil? %))
                                          (repeatedly #(.getNextEntry zip)))))))))
