(ns hermit.test.core
  (:require [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [hermit.core :refer :all]
            [midje.sweet :refer :all]))

(fact "Support functions work"
  (parent-path "blah/something/whatever.sh") => "blah/something/"
  (script-file "blah/something/whatever.sh") => "whatever.sh")

(fact "Lists resources in given resource url"
  (list-resources-under-url (io/resource "hermit/test"))  => (contains ["calls_hello_world.sh" "hello_world.sh"]
                                                             :gaps-ok :in-any-order))

(fact "Lists resources in given resource path"
  (list-resources-under-path "hermit/test")  => (contains ["hermit/test/calls_hello_world.sh" "hermit/test/hello_world.sh"]
               :gaps-ok :in-any-order))

(fact "Throws a nice exception if a resource doesn't exist"
  (rsh! "not/there.sh")
  => (throws NullPointerException "Resource 'not/' not found"))

(fact "Copying resources works"
  (let [tmp-dir (fs/temp-dir "hermit-test")]
    (copy-resources!
     [ "hermit/test/calls_hello_world.sh" "hermit/test/hello_world.sh"]
     "hermit/test/"
     tmp-dir)

    (fs/list-dir tmp-dir) => (contains ["calls_hello_world.sh" "hello_world.sh"]
                                       :gaps-ok :in-any-order)))

(fact "Calling a script works"
  (rsh! "hermit/test/hello_world.sh") => (contains {:out "Hello world\n"}))

(fact "Passes through args"
  (rsh! "hermit/test/echo_args.sh" "moo" "baa") => (contains {:out "moo baa\n"}))

(fact "Calling a script which relies on code in one or more resource paths (to be extracted) works"

  (with-deps ["hermit/test/subdir1"]
    (rsh! "hermit/test/sub_dir.sh")) => (contains {:out "Script in subdir1\n"})

  (with-deps ["hermit/test/subdir1" "hermit/test/subdir2"]
    (rsh! "hermit/test/sub_dirs.sh")) => (contains {:out "Script in subdir1\nScript in subdir2\n"}))
