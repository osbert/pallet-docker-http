(ns pallet-docker-http.apache
  (:require [clojure.string :as s]
            [clojure.walk :as walk]
            [pallet.phase :refer [phase-fn]]))

(defn generate-apache-config
  "Given config is a map that has a structure like this:

 {\"VirtualHost *:80\"
   [{\"DocumentRoot\" \"/path/to/root\"}
    {\"ServerName\" \"localhost\"}
    {\"ServerAlias\" \"awesome\"}
    {\"CustomLog\" \"/var/log/apache2/custom.log combined\"}
    {\"ProxyRequests\" \"Off\"}
    {\"ProxyVia\" \"Off\"}
    {\"ProxyPreserveHost\" \"On\"}
    {\"Proxy *\" [{\"AddDefaultCharset\" \"off\"}
                  {\"Order\" \"deny,allow\"}
                  {\"Allow\" \"from all\"}]}
    {\"ProxyPass\" \"/ http://127.0.0.1:3000/\"}]}

a suitable apache config file as a string is returned.
"
  [config]
  (s/join "\n"
          (for [[key val] config]
            (str "<" key ">\n"
                 (s/join "\n"
                         (for [m val]
                           (s/join "\n"
                                   (for [[k v] m]
                                     (if (vector? v)
                                       (generate-apache-config m)
                                       (str k " " v))))))
                 "\n</" (first (s/split key #" ")) ">\n"))))


(defn customize-proxy-config
  "Given an existing config structure, update the proxy destination."
  [config new-ip port]
  (walk/postwalk
   (fn [form]
     (if (and (map? form)
              (contains? form "ProxyPass"))
       {"ProxyPass" (format "/ http://%s:%s/" new-ip port)}
       form))
   config))

(defn config-service [service-name config]
  (phase-fn 
   (rf/remote-file (format "/etc/apache2/sites-available/%s" service-name)
                   :content (generate-apache-config config)
                   :owner "root" 
                   :group "root"
                   :mode "644")
   (exec-script* (format "a2ensite %s" service-name))))
