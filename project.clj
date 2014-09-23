(defproject savagematt/hermit "0.9-SNAPSHOT"
  :description "Run command line scripts bundled within your clojure project "

  :url "https://github.com/savagematt/hermit"

  :min-lein-version "2.3.4"

  :main hermit.core

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [me.raynes/fs "1.4.5"]
                 [me.raynes/conch "0.8.0"]]

  :plugins [[s3-wagon-private "1.1.2"]
            [lein-set-version "0.3.0"]]

  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.1.0"]]}})
