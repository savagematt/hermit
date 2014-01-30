hermit
=======

Clojure wrapper around command line programmes bundled within your project

Works from within the repl and within uberjars


## Installation

Add the following to the `:dependencies` section of your `project.clj` file:

```clj
    [hermit "0.3"]
```

## Usage

This example will run from within any project that imports hermit:
```clj
    (rsh! "hermit/hello_world.sh" "steve")
```

`hello_world.sh` is bundled with hermit.

The full contents of the parent directory of the script will be copied to a temp directory
before execution and all `.sh` files will be `chmod +x`.

The script with be run within the context of the temp directory

If your script has dependencies at other paths, you can do this:

```clj
    (with-deps [["hermit/" "bin"] "hermit"]
      (println (rsh! "hermit/call_deps.sh" "Dave")))
```

Again, `call_deps.sh` is bundled with hermit so this example will work..
