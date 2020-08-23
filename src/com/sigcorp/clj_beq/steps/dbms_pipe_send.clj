(ns com.sigcorp.clj-beq.steps.dbms-pipe-send
  (:require [taoensso.timbre :as log]
            [com.sigcorp.clj-beq.db :as db]
            [com.sigcorp.clj-beq.spec :as ss]
            [com.sigcorp.clj-beq.templates :refer [expand]])
  (:use [com.sigcorp.clj-beq.util]))

(defn reset-buffer
  "Resets the local message buffer.
  This is a wrapper for DBMS_PIPE.RESET_BUFFER"
  [db]
  (db/execute! db ["{ call dbms_pipe.reset_buffer }"]))

(defn pack-msg
  "Adds `item` to the local message buffer.
  This is a wrapper for DBMS_PIPE.PACK_MESSAGE(item)."
  [db item]
  (db/execute! db ["{ call dbms_pipe.pack_message(?) }" item]))

(defn send-msg
  "Sends the message in local message buffer to `pipe-name`.
  This is a wrapper for DBMS_PIPE.SEND_MESSAGE()"
  [db pipe-name timeout max-pipe-size]
  (let [res (db/query db ["select dbms_pipe.send_message(?, ?, ?) r from dual" pipe-name timeout max-pipe-size])]
    (log/debugf "send-msg: %s" (-> res first))
    (case (-> res first :r int)
      0 {:step-status :success}
      1 {:step-status :failure :cause :timeout}
      3 {:step-status :failure :cause :interrupt}
      {:step-status :failure :cause :unknown})))

(defn dbms-pipe-send-step
  [spec]
  (let [opts (conform-or-throw ::ss/dbms-pipe-send-opts "Invalid or missing dbms-pipe-send settings" spec)]
    (fn [{:keys [db] :as event}]
      (let [{:keys [pipe-name timeout max-pipe-size message-items]
             :or   {timeout       60
                    max-pipe-size 8192
                    message-items []}} (expand opts event)]
        (db/with-connection [conn db]
          (reset-buffer conn)
          (log/debugf "Sending message on pipe %s" pipe-name)
          (doall (map #(pack-msg conn %) message-items))
          (send-msg conn pipe-name timeout max-pipe-size))))))
