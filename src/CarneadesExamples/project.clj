;;; Copyright © 2010 Fraunhofer Gesellschaft 
;;; Licensed under the EUPL V.1.1

(defproject carneades-examples "1.0.0-SNAPSHOT"
  :description "Carneades is an argument mapping application, with a graphical 
user interface, and a software library for building applications supporting 
various argumentation tasks. This are the examples"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [carneades-engine "1.0.0-SNAPSHOT"]
                 [carneades-mapcomponent "1.0.0-SNAPSHOT"]]
  :dev-dependencies [[swank-clojure "1.3.0-SNAPSHOT"]])
