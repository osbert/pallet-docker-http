(ns pallet-docker-http.apache
  (:require [clojure.string :as s]
            [clojure.walk :as walk]
            [pallet.phase :refer [phase-fn]]
            [pallet.action.exec-script :refer [exec-script exec-script*]]
            [pallet.action.remote-file :as rf]
            [pallet.action :as action]
            [pallet.parameter :as parameter]
            [pallet.argument :as argument]
            [pallet.action.service :refer [service]]))

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

(defn config-service
  "Phase-fn to upload virtual host configuration file for service-name and a2ensite it."
  [service-name config]
  (phase-fn 
   (rf/remote-file (format "/etc/apache2/sites-available/%s" service-name)
                   :content (generate-apache-config config)
                   :owner "root" 
                   :group "root"
                   :mode "644")
   (exec-script* (format "a2ensite %s" service-name))))

(defn swap-proxy
  "Phase-fn to ask apache to proxy requests to a new IP address and reload apache2.

This should effectively act a zero-downtime deploy."
  [service-name config port session]
  (-> session
      (rf/with-remote-file
        (action/as-clj-action
         (fn [session local-path]
           (let [new-ip (s/trim (slurp local-path))]
             (if-not (s/blank? new-ip)
               (parameter/update-for-service session [(keyword service-name) :new-ip] (constantly new-ip))
               session)))
         [session local-path])
        (format "/tmp/%s.ipaddress" service-name))
      (rf/remote-file (format "/etc/apache2/sites-available/%s" service-name)
                      :content (argument/delayed [session]
                                                 (generate-apache-config
                                                  (customize-proxy-config
                                                   config
                                                   (parameter/get-for-service session [(keyword service-name) :new-ip] nil)
                                                   port)))
                      :owner "root" 
                      :group "root"
                      :mode "644"
                      :no-versioning true)
      (service "apache2" :action "reload")))
