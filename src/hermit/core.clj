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

(defn base-path
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

(defn list-resources
  "Given a resource path, returns all child resources within the same code source.

   context-path should be a file not a package, i.e. hermit/hello_world.sh, not
   hermit/

   hermit/hello_world.sh
   =>  a seq containing hermit/hello_world.sh"
  [context-path]
  (let [url            (parent-url context-path)
        protocol       (.getProtocol url)
        relative-paths (case protocol
                         "file" (list-dir-resources url)
                         "jar"  (list-jar-resources url))
        base-path      (base-path context-path)]
    (map #(str base-path %) relative-paths)))


(defn copy-resources!
  "Copy resources from a resource path to a directory, stripping relative-to
   from the resource names

   (copy-resources!
     \"hermit/hello_world.sh\"
     \"Some/directory\")
   => creates Some/directory/hello_world.sh
              Some/directory/some_other_script.sh

   (copy-resources!
     [\"hermit/hello_world.sh\" \"hermit/some_other_script.sh\"]
     \"hermit\"
     \"Some/directory\")
   => creates Some/directory/hello_world.sh
              Some/directory/some_other_script.sh"

  ([context-path dir]
   (copy-resources!
    (list-resources context-path)
    (base-path context-path)
    dir))
  ([resources relative-to dir]
   (fs/mkdirs dir)
   (let [relative-to-re (str/re-quote-replacement relative-to)]
     (doseq [resource resources]
       (let [relative-path (str/replace-first resource relative-to-re "")
             file (fs/file dir relative-path)]

         (fs/mkdirs (fs/parent file))

         (spit file (slurp (io/resource resource)))

         (when (= ".sh" (fs/extension file))
           (fs/chmod "+x" file)))))))

(defn rsh!
  "(rsh! \"hermit/hello_world.sh\" \"steve\")

   Given a path to a script which can be reached with clojure.java.io/resource, including
   scripts bundled in jars, executes the scripts using sh.

   The full contents of the parent directory of the script will be copied to a temp directory
   before execution and all .sh files will be chmod +x.

   The script with be run within the context of this temp directory"
  [script-path & args]
  (let [tmp-dir (fs/temp-dir "hermit")
        script  (fs/file tmp-dir (script-file script-path))]
    (copy-resources! script-path tmp-dir)
    (with-sh-dir tmp-dir
      (apply sh (.getAbsolutePath script) args))))

(defn -main [& args]
  (println (parent-url "hermit/hello_world.sh"))
  (println (apply rsh! "hermit/hello_world.sh" args))

  (shutdown-agents))
