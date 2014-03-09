hermit
=======

Clojure wrapper around command line programmes bundled within your project

Works from within the repl and within uberjars


## Installation

Add the following to the `:dependencies` section of your `project.clj` file:

```clj
    [savagematt/hermit "0.4"]
```

## Usage

This example will run from within any project that imports hermit:
```clj
    (rsh! "hermit/helloworld/hello_world.sh" "Dave")
```

`hello_world.sh` is bundled with hermit.

The full contents of the parent directory of the script will be copied to a temp directory
before execution and all `.sh` files will be `chmod +x`.

The script with be run within the context of the temp directory

If your script has dependencies in other packages, you can do this:

```clj
    (with-deps-in-package ["hermit/helloworld/hello_world.sh"]
      (println (rsh! "hermit/otherpackages/call_script_in_another_package.sh" "Dave")))
```

The full contents of the parent package of hermit/helloworld/hello_world.sh will be copied into the script directory.

If your script requires a dependent package to be in a different directory, you can alias it:

```clj
    (with-deps-in-package [["hermit/helloworld/hello_world.sh" "aliased_helloworld"]]
      (println (rsh! "hermit/otherpackages/call_script_in_aliased_package.sh" "Dave")))
```

Again, `call_script_in_aliased_package.sh` and `call_script_in_another_package.sh` are bundled with hermit so this example will work..
