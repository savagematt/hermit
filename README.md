hermit
=======

Clojure wrapper around command line programmes bundled within your project

Works from within the repl and within uberjars

## usage

This example will run from within any project that imports hermit:

    (rsh! "hermit/hello_world.sh" "steve")

hello_world.sh is bundled with hermit.

The full contents of the parent directory of the script will be copied to a temp directory
before execution and all `.sh` files will be `chmod +x`.

The script with be run within the context of the temp directory
