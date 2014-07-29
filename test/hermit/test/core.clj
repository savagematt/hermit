(ns hermit.test.core
  (:require [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [hermit.core :refer :all]
            [hermit.shell :refer :all]
            [midje.sweet :refer :all])
  (:import
    (java.io StringWriter)))

(defmacro with-temp-dir
  [[var prefix] & body]
  `(let [~var (fs/temp-dir ~prefix)]
     (try
       (do ~@body)
       (finally
         (fs/delete-dir ~var)))))

(fact "Support functions work"
      (parent-path "blah/something/whatever.sh") => "blah/something/"
      (script-file "blah/something/whatever.sh") => "whatever.sh"
      (.getPath (parent-url (io/resource "hermit/test/hello_world.sh"))) => #(.endsWith % "hermit/test/"))

(fact "Lists resources in given resource url"
      (resources-under-url (parent-url (io/resource "hermit/test/hello_world.sh")))
      => (contains ["calls_hello_world.sh" "hello_world.sh" "subdir1/sub_dir1.sh"]
                   :gaps-ok
                   :in-any-order))

(fact "Lists resources in given resource path"
      (resources-in-parent-package "hermit/test/hello_world.sh") => (contains ["hermit/test/calls_hello_world.sh" "hermit/test/hello_world.sh"]
                                                                              :gaps-ok :in-any-order))

(fact "Throws a nice exception if a resource doesn't exist"
      (rsh! "not/there.sh")
      => (throws NullPointerException "Resource 'not/there.sh' not found"))

(fact "Copying resources works"
      (with-temp-dir [tmp-dir "hermit-test"]
                     (copy-resources!
                       ["hermit/test/calls_hello_world.sh" "hermit/test/hello_world.sh"]
                       "hermit/test/"
                       tmp-dir)

                     (fs/list-dir tmp-dir) => (contains ["calls_hello_world.sh" "hello_world.sh"]
                                                        :gaps-ok :in-any-order)))

(fact "Copying resources works"
      (with-temp-dir [tmp-dir "hermit-test"]
                     (copy-resources!
                       ["hermit/test/subdir1/sub_dir1.sh"]
                       "hermit/test/"
                       tmp-dir)

                     (fs/list-dir tmp-dir) => (contains ["subdir1"]
                                                        :gaps-ok :in-any-order)

                     (fs/list-dir (fs/file tmp-dir "subdir1"))
                     => (contains ["sub_dir1.sh"]
                                  :gaps-ok :in-any-order)

                     (fs/file? (fs/file tmp-dir "subdir1/sub_dir1.sh"))))

(fact "Calling a script works"
      (rsh! "hermit/test/hello_world.sh") => (contains {:out "Hello world\n"}))

(fact "Passes through args"
      (rsh! "hermit/test/echo_args.sh" "moo" "baa" "eeyore") => (contains {:out "moo baa eeyore\n"}))

(fact "Passes through args with timeout"
      (let [result (with-sh-opts {:timeout 2000} (rsh! "hermit/test/echo_args.sh" "moo" "baa" "eeyore"))]
        (:out result) => (contains "moo baa eeyore\n")
        (:exit result) => 0))

(fact "Passes through args with timeout expiry"
      (let [result (with-sh-opts {:timeout 50} (rsh! "hermit/test/slow_echo_args.sh" "moo" "baa" "eeyore"))]
        (:out result) => (is "slow\n")
        (:exit result) => :timeout))

(fact "Writes to a stream"
      (let [my-out (StringWriter.)]
        (with-sh-opts {:out my-out} (rsh! "hermit/test/echo_args.sh" "moo" "baa" "eeyore"))
        (str my-out)) => (contains "moo baa eeyore\n"))

(fact "Writes to a stream with timeout"
      (let [my-out (StringWriter.)]
        (with-sh-opts {:out my-out :timeout 2000} (rsh! "hermit/test/echo_args.sh" "moo" "baa" "eeyore"))
        (str my-out)) => (contains "moo baa eeyore\n"))

(fact "Writes to a stream with timeout expiry"
      (let [my-out (StringWriter.)
            result (with-sh-opts {:out my-out :timeout 50} (rsh! "hermit/test/slow_echo_args.sh" "moo" "baa" "eeyore"))]
        (str my-out) => (is "slow\n")
        (:exit result) => :timeout))

(fact "Calling a script which relies on code in one or more resource paths (to be extracted) works"
      (with-deps-in-package ["hermit/test/subdir1/sub_dir1.sh"]
                            (rsh! "hermit/test/sub_dir.sh")) => (contains {:out "Script in subdir1\n"})

      (with-deps-in-package ["hermit/test/subdir1/sub_dir1.sh" "hermit/test/subdir2/sub_dir2.sh"]
                            (rsh! "hermit/test/sub_dirs.sh")) => (contains {:out "Script in subdir1\nScript in subdir2\n"}))

(fact "Can overload the path dependencies are unpacked to"
      (with-deps-in-package [["hermit/test/subdir1/sub_dir1.sh" "bin"]]
                            (rsh! "hermit/test/call_from_bin.sh")) => (contains {:out "Script in subdir1\n"}))

(fact "Can overload the path dependencies are unpacked to"
      (with-deps-in-package [["hermit/test/subdir1/sub_dir1.sh" "bin"]]
                            (rsh! "hermit/test/call_from_bin.sh")) => (contains {:out "Script in subdir1\n"}))

