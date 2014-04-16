(ns pallet-docker-http.core
  (:require [clojure.string :as s]
            [pallet.action.exec-script :refer [exec-script exec-script*]]
            [pallet.action.remote-file :as rf]
            [pallet.action :as action]
            [pallet.parameter :as parameter]
            [pallet.argument :as argument]))

(defn container-name
  "Return a relatively unique container name based on the current time with the desired prefix."
  ([prefix] (container-name prefix (java.util.Calendar/getInstance)))
  ([prefix now] (format "%s-%04d%02d%02d-%02d%02d"
                        prefix
                        (.get now java.util.Calendar/YEAR)
                        (inc (.get now java.util.Calendar/MONTH))
                        (.get now java.util.Calendar/DAY_OF_MONTH)
                        (.get now java.util.Calendar/HOUR_OF_DAY)
                        (.get now java.util.Calendar/MINUTE))))

(defn container-prefix
  "Given the container name, return the unique prefix."
  [container-name]
  (-> container-name
      (s/split #"-")
      first))

(defn repeat-until-success
  "Return script that repeatedly loops until exit status is zero."
  [test-script]
  (format "while ! %s; do sleep 5; done" test-script))

(defn wait-for-http
  "Return script that waits until HTTP service is up for the given URL."
  [url]
  (repeat-until-success (format "curl %s 2>/dev/null >/dev/null" url)))

(defn find-docker-ip
  "Return script that returns (and writes to /tmp) the IP addres for a given container."
  [container-name]
  (format "docker inspect %s | grep IPAddress | cut -f 2 -d : | cut -f 2 -d \\\" | tee /tmp/%s.ipaddress"
          container-name
          (container-prefix container-name)))

(defn wait-for-service
  "Return script that blocks until HTTP service is up on http://container-name:port"
  [container-name port]
  (s/join "\n"
          [(format "IP=$(%s)" (find-docker-ip container-name))
           (wait-for-http (format "http://$IP:%s" port))]))

(defn find-existing-container
  "Return script that returns the most recently launched currently running container with the specified prefix."
  [container-prefix]
  (format "docker ps | grep %s | cut -f 1 -d ' ' | head -n 1 > /tmp/%s.container" container-prefix container-prefix))

(defn stop-old-container [session service-name]
  (-> session
      (rf/with-remote-file
        (action/as-clj-action
         (fn [session local-path]
           (let [running-container (s/trim (slurp local-path))]
             (if-not (s/blank? running-container)
               (parameter/update-for-service session [(keyword service-name) :running-container] (constantly running-container))
               session)))
         [session local-path])
        (format "/tmp/%s.container" service-name))
      (exec-script*
       (argument/delayed [session]
                         (format "docker stop %s"
                                 (parameter/get-for-service session
                                                            [(keyword service-name) :running-container]
                                                            nil))))))

(defn start-new-container [session service-name docker-image-name port]
  (let [cname (container-name service-name)]
    (-> session
        (exec-script* (find-existing-container service-name))
        (exec-script* (format "docker run -d --name %s %s" cname docker-image-name))
        (exec-script* (wait-for-service cname port)))))

