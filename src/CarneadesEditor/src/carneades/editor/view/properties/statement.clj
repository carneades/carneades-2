;;; Copyright © 2010 Fraunhofer Gesellschaft 
;;; Licensed under the EUPL V.1.1

(ns ^{:doc "Function to display statement properties in the panel properties."}
  carneades.editor.view.properties.statement
  (:use clojure.contrib.def
        clojure.contrib.swing-utils
        (carneades.editor.utils seq listeners))
  (:import carneades.editor.uicomponents.StatementPropertiesView
           (javax.swing KeyStroke Action AbstractAction)))

(defvar- *statementProperties* (StatementPropertiesView.))
(defvar- *statementTextArea* (.statementTextArea *statementProperties*))
(defvar *statementStatusComboBox* (.statusComboBox *statementProperties*))
(defvar *statementProofstandardComboBox* (.proofstandardComboBox *statementProperties*))
(defvar- *acceptableText* (.acceptableText *statementProperties*))
(defvar- *complementAcceptableText* (.complementAcceptableText *statementProperties*))

(defvar- *mapTitleText* (.mapTitleText *statementProperties*))
(defvar- *pathText* (.pathText *statementProperties*))

(defvar- *statuses* {:stated "Stated"
                     :accepted "Accepted"
                     :rejected "Rejected"
                     :questioned "Questioned"})

(defvar- *txt-to-status* (reverse-map *statuses*))

(defvar- *proofstandards* {;; :ba "Preponderance of Evidence"
                           :pe "Preponderance of Evidence"
                           :brd "Beyond Reasonable Doubt"
                           :cce "Clear and Convincing Evidence"
                           :dv "Dialectical Validity"
                           :se "Scintilla of Evidence"})

(defvar- *txt-to-proofstandard* (reverse-map *proofstandards*))

(gen-listeners-fns "statement-edit")

(defn init-statement-properties [])

(defvar- *previous-statement-content* (atom {}))

(defn- set-enter-edit-statement []
  ;; see http://stackoverflow.com/questions/2162170/jtextarea-new-line-on-shift-enter
  (let [txtsubmit "text-submit"
        insertbreak "insert-break"
        input (.getInputMap *statementTextArea*)
        enter (KeyStroke/getKeyStroke "ENTER")
        shiftenter (KeyStroke/getKeyStroke "shift ENTER")
        actions (.getActionMap *statementTextArea*)]
    (.put input shiftenter insertbreak)
    (.put input enter txtsubmit)
    (.put actions txtsubmit (proxy [AbstractAction] []
                              (actionPerformed
                               [event]
                               (call-statement-edit-listeners event))))))

(defn get-statement-properties-panel [path id maptitle stmt stmt-str status proofstandard acceptable complement-acceptable]
  (.setText *pathText* path)
  (.setText *mapTitleText* maptitle)
  (.setText *statementTextArea* stmt)
  (reset! *previous-statement-content* {:path path :id id :previous-content stmt
                                        :previous-status status
                                        :previous-proofstandard proofstandard})
  (.setSelectedItem *statementStatusComboBox* (get *statuses* status))
  (.setSelectedItem *statementProofstandardComboBox* (get *proofstandards* proofstandard))
  (if acceptable
    (.setText *acceptableText* "Acceptable")
    (.setText *acceptableText* "Not Acceptable"))
  (if complement-acceptable
    (.setText *complementAcceptableText* "Complement Acceptable")
    (.setText *complementAcceptableText* "Complement Not Acceptable"))
  (set-enter-edit-statement)
  *statementProperties*)

(defn statement-being-edited-info []
  (merge {:content (.getText *statementTextArea*)
          :status (*txt-to-status* (.getSelectedItem *statementStatusComboBox*))
          :proofstandard (*txt-to-proofstandard* (.getSelectedItem *statementProofstandardComboBox*))}
         (deref *previous-statement-content*)))

