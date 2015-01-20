;;; Copyright © 2010 Fraunhofer Gesellschaft 
;;; Licensed under the EUPL V.1.1

(ns ^{:doc "Messages used in the views of the assistant implementations."}
  carneades.editor.view.application.wizards.messages
  (:use clojure.contrib.def))

(defvar *position-n-of* "Position %d of %d:")
(defvar *position* "Position:")

(defvar *clause-n-of* "Clause %d of %d:")
(defvar *suggestion-n-of* "Suggestion %d of %d:")

(defvar *literal* "Literal:")
(defvar *predicate* "Predicate:")