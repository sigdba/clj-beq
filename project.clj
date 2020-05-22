(defproject com.sigcorp/clj-beq "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.clojure/tools.cli "1.0.194"]          ; Command-line parsing
                 [clj-commons/clj-yaml "0.7.0"]             ; YAML parsing
                 [expound "0.8.4"]                          ; Improved spec messages
                 ;;[selmer "1.12.24"]                       ; Template expansion

                 [com.taoensso/timbre "4.10.0"]             ; Logging

                 ;;[org.clojure/tools.logging "1.1.0"]
                 ;;[org.apache.logging.log4j/log4j-api "2.13.2"]
                 ;;[org.apache.logging.log4j/log4j-core "2.13.2"]

                 ;; TODO: Not sure this should be a runtime dependency.
                 [com.twilio.sdk/twilio "7.50.1"]           ; Twilio text-messaging

                 ;; mvn install:install-file -X -DgroupId=local -DartifactId=ojdbc8 -Dversion=19.3 -Dpackaging=jar -Dfile=ojdbc8.jar -DgeneratePom=true
                 [local/ojdbc8 "19.3"]]

  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}}

  :main com.sigcorp.clj-beq.cli
  :aot [com.sigcorp.clj-beq.cli]
  :repl-options {:init-ns com.sigcorp.clj-beq})
