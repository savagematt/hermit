(ns hermit.core
  (:require [clojure.java.shell :refer [sh with-sh-dir]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [hermit.jars :refer :all]
            [clojure.pprint :refer [pprint]])
  (:import [java.net URL URI])
  (:gen-class))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn ensure-trailing-slash [s] (str/replace s #"[^/]$" #(str %1 "/")))

(defn parent-url
  "For jar resources,  hermit/helloworld/hello_world.sh => jar:file:/Path/to/jar!hermit/helloworld/
   For file resources, hermit/helloworld/hello_world.sh =>     file:/Path/to/project/src/hermit/helloworld/"
  [url]
  (case (.getProtocol url)
    "jar"  (parent-jar-url url)
    "file" (.toURL (.resolve (.toURI url) "."))))

(defn parent-path
  "hermit/helloworld/hello_world.sh
   => hermit/helloworld/"
  [context-path]
  (str/replace context-path #"[^/]*$" ""))

(defn script-file
  "hermit/helloworld/hello_world.sh
   => hello_world.sh"
  [context-path]
  (re-find #"[^/]*$" context-path))

(defn dir-resources
  "Given a directory url, returns the paths of all files under that directory,
   relative to the directory.

  file:/Path/to/project/src/hermit/helloworld
  =>  a seq containing hello_world.sh"
  [url]
  (let [root-dir      (fs/file url)
        root-dir-path (str (str/re-quote-replacement (.getAbsolutePath root-dir)) "/")]
    (assert (fs/directory? root-dir) (str url "is not a directory"))
    (map
     #(str/replace-first % root-dir-path "")
     (filter #(.isFile %) (file-seq root-dir)))))

(defn resources-under-url
  "Given a url, returns all child resources within the same code source.

   jar:file:/Path/to/jar!hermit
   =>  a seq containing hello_world.sh"
  [url]
  (case (.getProtocol url)
    "file" (dir-resources url)
    "jar"  (jar-resources url)))

(defn resources-in-parent-package
  "Given a resource path, returns all child resources within the same code source.

   hermit/
   =>  a seq containing hermit/hello_world.sh"
  [reference-resource-path]
  (let [resource-url (io/resource reference-resource-path)]
    (when-not resource-url
      (throw (NullPointerException. (str "Resource '" reference-resource-path "' not found"))))

    (map #(str (parent-path reference-resource-path) %)
         (resources-under-url (parent-url resource-url)))))


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
  ([reference-resource-path dir]
     (copy-resources!
      (resources-in-parent-package reference-resource-path)
      (parent-path reference-resource-path)
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
  (let [hermit-dir (if (thread-bound? #'*hermit-dir*) *hermit-dir* (fs/temp-dir "hermit"))]
    (try
      (copy-resources! script-path hermit-dir)
      (with-sh-dir hermit-dir
                   (apply sh (.getAbsolutePath (fs/file hermit-dir (script-file script-path))) args))
      (finally
        ; If this function was responsible for creating
        ; hermit-dir, we should also clean it up
        (when-not (thread-bound? #'*hermit-dir*)
          (fs/delete-dir hermit-dir))))))

(defn dependency-output
  [output dir]
  (if (sequential? output)
    [(first output) (fs/file dir (second output))]
    [output (fs/file dir output)]))

(defmacro with-deps-in-package
  "Unpacks resource-paths into the target hermit directory

  Usage: (with-deps [\"package/one/reference_resource.sh\"
                     [\"package/two/reference_resource.sh\" \"unpack/dir\"]]
            (rsh! \"some/package/script_depending_on_other_namespaces.sh\")"
  [reference-resource-paths & body]
  `(let [should-create-hermit-dir# (not (thread-bound? #'*hermit-dir*))]
      (binding [*hermit-dir* (if should-create-hermit-dir# (fs/temp-dir "hermit") *hermit-dir*)]
         (try
           (doseq [dependency-output# (list ~@reference-resource-paths)]
             (let [[dependency-path# dependency-dir#] (dependency-output dependency-output# *hermit-dir*)]
               (copy-resources! dependency-path# dependency-dir#)))
           (do ~@body)

           (finally
             (when should-create-hermit-dir#
               (fs/delete-dir *hermit-dir*))))))
  )

(defn -main [& args]
  (println "If you executed the jar with a single argument \"Dave\", :out should be \"Hello Dave\"")
  (with-deps-in-package [["hermit/helloworld/hello_world.sh" "aliased_helloworld"]]
                        (println (rsh! "hermit/otherpackages/call_script_in_aliased_package.sh" "Dave")))

  (shutdown-agents))
