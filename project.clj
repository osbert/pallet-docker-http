(defproject pallet-docker-http "0.1.0-SNAPSHOT"
  :description "Library to help with deploying HTTP services in a Docker container."
  :url "http://github.com/osbert/pallet-docker-http"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.cloudhoist/pallet "0.7.2"]]
  :local-repo-classpath true
  :repositories {"sonatype-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
                 "sonatype" "https://oss.sonatype.org/content/repositories/releases/"})
