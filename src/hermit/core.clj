(ns hermit.core
  (:require [clojure.java.shell :refer [sh with-sh-dir]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [hermit.jars :refer :all])
  (:import [java.net URL URI])
  (:gen-class))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn ensure-trailing-slash [s] (str/replace s #"[^/]$" #(str %1 "/")))

(defn parent-url
  "For jar resources,  hermit/hello_world.sh => jar:file:/Path/to/jar!hermit/
   For file resources, hermit/hello_world.sh =>     file:/Path/to/project/src/hermit/"
  [resource-path]
  (let [resource-url (io/resource resource-path)]

    (when-not resource-url
      (throw (NullPointerException. (str "Resource '" resource-path "' not found"))))

    (case (.getProtocol resource-url)
      "jar"  (parent-jar-url resource-url)
      "file" (.toURL (.resolve (.toURI resource-url) ".")))))

(defn parent-path
  "hermit/hello_world.sh
   => hermit/"
  [context-path]
  (str/replace context-path #"[^/]*$" ""))

(defn script-file
  "hermit/hello_world.sh
   => hello_world.sh"
  [context-path]
  (re-find #"[^/]*$" context-path))

(defn list-dir-resources
  "Given a directory url, returns the paths of all files under that directory,
   relative to the directory.

  file:/Path/to/project/src/hermit/
  =>  a seq containing hello_world.sh"
  [url]
  (let [base-file      (fs/file url)
        base-file-path (str (str/re-quote-replacement (.getAbsolutePath base-file)) "/")]
    (map
     #(str/replace-first % base-file-path "")
     (filter #(.isFile %) (file-seq base-file)))))

(defn list-resources-under-url
  "Given a url, returns all child resources within the same code source.

   jar:file:/Path/to/jar!hermit
   =>  a seq containing hello_world.sh"
  [url]
  (case (.getProtocol url)
    "file" (list-dir-resources url)
    "jar"  (list-jar-resources url)))

(defn list-resources-under-path
  "Given a resource path, returns all child resources within the same code source.

   hermit/
   =>  a seq containing hermit/hello_world.sh"
  [resource-path]
  (let [resource-url (io/resource resource-path)]
    (when-not resource-url
      (throw (NullPointerException. (str "Resource '" resource-path "' not found"))))

    (map #(str (ensure-trailing-slash resource-path) %)
         (list-resources-under-url resource-url))))


(defn copy-resources!
  "Copy resources from a resource path to a directory, stripping relative-to
   from the resource names

   (copy-resources!
     \"hermit\"
     \"Some/directory\")
   => creates Some/directory/hello_world.sh
              Some/directory/some_other_script.sh

   (copy-resources!
     [\"hermit/hello_world.sh\" \"hermit/some_other_script.sh\"]
     \"hermit\"
     \"Some/directory\")
   => creates Some/directory/hello_world.sh
              Some/directory/some_other_script.sh"
  ([resource-path dir]
     (copy-resources!
      (list-resources-under-path resource-path)
      resource-path
      dir))
  ([resources relative-to dir]
     (fs/mkdirs dir)
     (let [relative-to-re (str/re-quote-replacement (ensure-trailing-slash relative-to))]
       (doseq [resource resources]
         (let [relative-path (str/replace-first resource relative-to-re "")
               file (fs/file dir relative-path)]

           (fs/mkdirs (fs/parent file))

           (io/copy (io/input-stream (io/resource resource)) file)

           (when (= ".sh" (fs/extension file))
             (fs/chmod "+x" file)))))))

(declare ^:dynamic *hermit-dir*)

(defn rsh!
  "(rsh! \"hermit/hello_world.sh\" \"steve\")

   Given a path to a script which can be reached with clojure.java.io/resource, including
   scripts bundled in jars, executes the scripts using sh.

   The full contents of the parent directory of the script will be copied to a temp directory
   before execution and all .sh files will be chmod +x.

   The script with be run within the context of this temp directory"
  [script-path & args]
  (let [hermit-dir (if (thread-bound? #'*hermit-dir*) *hermit-dir* (fs/temp-dir "hermit"))
        script  (fs/file hermit-dir (script-file script-path))]
    (copy-resources! (parent-path script-path) hermit-dir)
    (with-sh-dir hermit-dir
      (apply sh (.getAbsolutePath script) args))))

(defmacro with-deps [resource-paths & body]
  `(binding [*hermit-dir* (fs/temp-dir "hermit")]
     (doseq [dependency-path# (list ~@resource-paths)]
       (copy-resources! dependency-path# *hermit-dir*))
     (do ~@body))
  )

(defn -main [& args]
  (println (parent-url "hermit/hello_world.sh"))
  (println (apply rsh! "hermit/hello_world.sh" args))

  (shutdown-agents))
