# pallet-docker-http

A Clojure library designed to help deploy Docker containers that
provide HTTP services using pallet. It provides methods to aid in:

* Container naming,
* Waiting for HTTP service to become available
* Finding, starting, and stopping currently running containers.
* Zero-downtime deploys by changing Apache's reverse proxy configuration

## Usage

1. Start a new container
1. Wait for HTTP service to come up
1. Swap proxy configuration, send graceful reload.
1. Stop old container

```Clojure
;; Add osbert/pallet-docker-http to your project.clj dependencies.
[osbert/pallet-docker-http "0.1.0-SNAPSHOT"]

(ns my.app
  (:require [pallet-docker-http.core :as c]
            [pallet-docker-http.apache :as a]
            [pallet.core :refer [phase-fn]]
            [pallet.compute :refer [compute-service))

;; NOTE: The ProxyPass destination here does not matter, it will get
;; overridden by a/swap-proxy later to be correct based on runtime
;; information.

(def myapp-apache-config
  {"VirtualHost *:80"
   [{"DocumentRoot" "/path/to/root"}
    {"ServerName" "myapp.mydomain"}
    {"CustomLog" "/var/log/apache2/myapp.log combined"}
    {"ProxyRequests" "Off"}
    {"ProxyVia" "Off"}
    {"ProxyPreserveHost" "On"}
    {"Proxy *" [{"AddDefaultCharset" "off"}
                {"Order" "deny,allow"}
                {"Allow" "from all"}]}
                {"ProxyPass" "/ http://127.0.0.1:3000/"}]})

;; NOTE: Currently based on slightly older versions of pallet,
;; phase-fn is a macro around -> so provide direct function bindings.

(def swap-proxy-myapp (partial a/swap-proxy "myapp" myapp-apache-config 8080))
(def stop-old-myapp (partial c/stop-old-container "myapp"))
(def start-myapp (partial c/start-new-container "myapp" "mycontainer" 8080))

(def my-servers
  (compute-service
    "node-list"
    :node-list [(make-node "webserver" "myservers" "WEBSERVER-IP-ADDRESS" :ubuntu)]))

(defn lift-prod-web
  [phase-fns]
  (pallet.core/lift
   (pallet.core/group-spec "myservers"
                           :phases { :configure phase-fns })
   :compute my-servers
   :user (make-user "www-data" :no-sudo true)))

(defn deploy-myapp []
  ;; NOTE: provide these functions, beyond the scope of this project.
  (build-docker-image)
  (load-docker-image)

  ;; NOTE: web user must have the right to modify and reload Apache config.
  (lift-prod-web (phase-fn start-myapp swap-proxy-myapp stop-old-myapp))) 
```
  
## License

Copyright © 2014 Osbert Feng

Distributed under the Eclipse Public License, the same as Clojure.
