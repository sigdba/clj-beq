(defproject com.sigcorp/clj-beq "0.2.0-SNAPSHOT"
  :description "Clojure library and utility for managing the Banner event queue"
  :url "https://github.com/sigdba/clj-beq"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.clojure/tools.cli "1.0.194"]          ; Command-line parsing
                 [clj-commons/clj-yaml "0.7.0"]             ; YAML parsing
                 [expound "0.8.4"]                          ; Improved spec messages
                 [selmer "1.12.28"]                         ; Template expansion

                 [com.taoensso/timbre "4.10.0"]             ; Logging

                 ;;[org.clojure/tools.logging "1.1.0"]
                 ;;[org.apache.logging.log4j/log4j-api "2.13.2"]
                 ;;[org.apache.logging.log4j/log4j-core "2.13.2"]

                 ;; TODO: Not sure this should be a runtime dependency if we're shipping this as a library.
                 [com.twilio.sdk/twilio "7.50.1"]           ; Twilio text-messaging
                 [clj-http "3.10.1"]                        ; REST client
                 [cheshire "5.10.0"]                        ; JSON parsing

                 [com.oracle.database.jdbc/ojdbc8 "19.3.0.0"]]

  :profiles {:dev {:dependencies [[org.clojure/test.check "0.10.0"]]}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.632"]]}}

  :aliases {"test" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner" "--plugin" "kaocha.plugin.alpha/spec-test-check"
                    "--reporter" "documentation"]}

  :main com.sigcorp.clj-beq.cli
  :aot [com.sigcorp.clj-beq.cli]
  :repl-options {:init-ns com.sigcorp.clj-beq})
