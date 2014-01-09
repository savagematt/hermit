(defproject hermit "0.3-SNAPSHOT"
  :description "Run command line scripts bundled within your clojure project "

  :url "https://github.com/savagematt/hermit"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :main hermit.core

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [me.raynes/fs "1.4.5"]]

  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.1.0"]]}})
