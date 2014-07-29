(ns hermit.shell
  (:require
    [me.raynes.conch.low-level :as conch]))

(def ^:dynamic *sh-dir* nil)
(def ^:dynamic *sh-opts* {})

(defn sh
  "Behaves like the normal clojure.java.shell/sh but can be run with a *sh-opts* binding map of :out to an output stream (eg. *out* for stdout) and :timeout in millis
  Gets a little strange with timeouts and output to streams - the return values become slightly meaningless, or sometimes refs"
  [& args]
  (let [input (cond *sh-dir* (conj (vec args) :dir *sh-dir*) :else args)
        process (apply conch/proc input)
        timeout (:timeout *sh-opts*)
        out (:out *sh-opts*)
        out-fn (cond
                 out (fn [] (conch/stream-to process :out out))
                 :else (fn [] (conch/stream-to-string process :out)))
        exit-fn (cond
                  timeout (fn [] (conch/exit-code process timeout))
                  :else (fn [] (conch/exit-code process)))
        result (cond
                 timeout (future (out-fn))
                 :else (out-fn))
        exit-code (exit-fn)]

    {:out result :exit exit-code}))

(defmacro with-sh-dir [dir & body]
  `(binding [*sh-dir* ~dir]
     (do ~@body)))

(defmacro with-sh-opts [sh-opts & body]
  `(binding [*sh-opts* ~sh-opts]
     (do ~@body)))