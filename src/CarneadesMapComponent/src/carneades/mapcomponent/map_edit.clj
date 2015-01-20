;;; Copyright © 2010 Fraunhofer Gesellschaft 
;;; Licensed under the EUPL V.1.1

(ns carneades.mapcomponent.map-edit
  (:use clojure.contrib.def
        clojure.contrib.pprint
        carneades.mapcomponent.map
        carneades.mapcomponent.map-styles
        carneades.engine.argument
        carneades.engine.statement)
  (:import (carneades.mapcomponent.map StatementCell ArgumentCell PremiseCell)
           javax.swing.SwingConstants
           (com.mxgraph.util mxConstants mxUtils mxCellRenderer mxPoint mxEvent
                             mxEventSource$mxIEventListener mxUndoManager)
           com.mxgraph.swing.util.mxGraphTransferable
           com.mxgraph.swing.handler.mxRubberband
           (com.mxgraph.view mxGraph mxStylesheet)
           (com.mxgraph.model mxCell mxGeometry)
           com.mxgraph.layout.mxStackLayout
           com.mxgraph.swing.mxGraphComponent
           com.mxgraph.swing.mxGraphOutline))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; private functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- change-cell-and-styles [component ag
                               update-statement-object
                               update-statement-style
                               update-premise-object
                               update-premise-style
                               update-argument-object
                               update-argument-style
                               update-argument-edge-style]
  (let [graph (.getGraph component)
        model (.getModel graph)]
    (let [p (.getDefaultParent graph)]
      (doseq [cell (.getChildCells graph p true true)]
        (let [val (.getValue cell)]
          (cond (instance? StatementCell val)
                (do
                  (.setValue model cell (update-statement-object val))
                  (adjust-size graph cell)
                  (.setStyle model cell (update-statement-style val (.getStyle cell))))

                (instance? PremiseCell val)
                (do
                  (.setValue model cell (update-premise-object val))
                  (.setStyle model cell (update-premise-style val (.getStyle cell))))
                  
                (and (instance? ArgumentCell val) (.isVertex cell))
                (do
                  (.setValue model cell (update-argument-object val))
                  (.setStyle model cell (update-argument-style val (.getStyle cell))))

                (and (instance? ArgumentCell val) (.isEdge cell))
                (do
                  (.setValue model cell (update-argument-object val))
                  (.setStyle model cell (update-argument-edge-style val (.getStyle cell))))))))))

(defn- do-not-change-style [userobject style]
  style)

(defn- update-stmt-object [userobject newag]
  (let [stmt-str (:stmt-str userobject)
        stmt (:stmt userobject)]
    (let [full (stmt-to-str newag stmt stmt-str)]
     (StatementCell. newag stmt stmt-str
                     (trunk full) full))))

(defn- update-stmt-style [userobject oldstyle ag]
  (get-statement-style ag (:stmt userobject)))

(defn- update-argument-style [userobject oldstyle ag]
  (get-argument-style ag (get-argument ag (:id (:arg userobject)))))

(defn- update-argument-object [userobject ag]
  (let [id (:id (:arg userobject))
        arg (get-argument ag id)]
    (ArgumentCell. arg)))

(defn- change-all-cell-and-styles
  ([component ag]
     (change-all-cell-and-styles component ag identity do-not-change-style))
  
  ([component ag update-pm-object update-pm-style]
     (letfn [(update-argument-edge-style
              [userobject oldstyle]
              (get-conclusion-edge-style (get-argument ag (:id (:arg userobject)))))]
       (change-cell-and-styles component ag
                               #(update-stmt-object % ag)
                               #(update-stmt-style %1 %2 ag)
                               update-pm-object
                               update-pm-style
                               #(update-argument-object %1 ag)
                               #(update-argument-style %1 %2 ag)
                               update-argument-edge-style))))

(defn- change-premise-content-and-style [component ag oldarg arg pm]
  (letfn [(update-premise-object
           [userobject]
           (let [cellargid (:id (:arg userobject))
                 oldpm (:pm userobject)]
             (if (and (= cellargid (:id oldarg))
                      (= (:atom pm) (:atom oldpm)))
               (PremiseCell. arg pm)
               userobject)))
            
          (update-premise-style
           [userobject oldstyle]
           (let [oldpm (:pm userobject)
                 cellarg (:arg userobject)
                 style (get-edge-style oldpm)
                 cellargid (:id (:arg userobject))]
             (if (and (= cellargid (:id oldarg))
                      (= (:atom pm) (:atom oldpm)))
               (get-edge-style pm)
               style)))]
    (change-all-cell-and-styles component ag update-premise-object update-premise-style)))

(defn- update-arg-in-pm [userobject arg]
  (let [cellargid (:id (:arg userobject))]
    (if (= cellargid (:id arg))
      (PremiseCell. arg (:pm userobject))
      userobject)))

(defn- update-arg [userobject arg]
  (let [cellargid (:id (:arg userobject))]
    (if (= cellargid (:id arg))
      (ArgumentCell. arg)
      userobject)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn change-title [graphcomponent ag title]
  (let [component (:component graphcomponent)]
    (with-transaction component
      (change-all-cell-and-styles component ag))))

(defn change-premise-polarity [graphcomponent ag oldarg arg pm]
  (let [component (:component graphcomponent)]
    (with-transaction component
      (change-premise-content-and-style component ag oldarg arg pm))))

(defn change-premise-type [graphcomponent ag oldarg arg pm]
  (let [component (:component graphcomponent)]
    (with-transaction component
     (change-premise-content-and-style component ag oldarg arg pm))))

(defn change-premise-role [graphcomponent ag oldarg arg pm]
  (let [component (:component graphcomponent)]
    (with-transaction component
      (change-premise-content-and-style component ag oldarg arg pm))))

(defn change-argument-title [graphcomponent ag arg title]
  (let [component (:component graphcomponent)]
    (with-transaction component
     (change-cell-and-styles component ag
                             #(update-stmt-object % ag) do-not-change-style
                             #(update-arg-in-pm % arg) do-not-change-style
                             #(update-arg % arg) do-not-change-style
                             do-not-change-style))))

(defn change-argument-scheme [graphcomponent ag arg scheme]
  (let [component (:component graphcomponent)]
    (with-transaction component
     (change-cell-and-styles component ag
                             #(update-stmt-object % ag) do-not-change-style
                             #(update-arg-in-pm % arg) do-not-change-style
                             #(update-arg % arg) do-not-change-style
                             do-not-change-style))))

(defn change-argument-weight [graphcomponent ag arg title]
  (let [component (:component graphcomponent)]
    (with-transaction component
     (change-cell-and-styles component ag
                             #(update-stmt-object % ag) #(update-stmt-style %1 %2 ag)
                             #(update-arg-in-pm % arg) do-not-change-style
                             #(update-arg % arg) #(update-argument-style %1 %2 ag)
                             do-not-change-style))))

(defn change-statement-status [graphcomponent ag stmt]
  (let [component (:component graphcomponent)]
    (with-transaction component
      (change-all-cell-and-styles component ag))))

(defn change-statement-proofstandard [graphcomponent ag stmt]
  (let [component (:component graphcomponent)]
    (with-transaction component
      (change-all-cell-and-styles component ag))))

(defn change-argument-direction [graphcomponent ag arg direction]
  (let [component (:component graphcomponent)]
    (with-transaction component
     (change-all-cell-and-styles component ag))))

(defn add-premise [graphcomponent ag arg stmt]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        model (.getModel graph)
        p (.getDefaultParent graph)]
    (let [argcell (find-argument-cell graph (:id arg))
          stmtcell (find-statement-cell graph stmt)
          premise (get-premise arg (statement-atom stmt))]
      (with-transaction component
        (insert-edge graph p (PremiseCell. arg premise) stmtcell argcell
                     (get-edge-style premise))
        (change-all-cell-and-styles component ag)))))

(defn delete-premise [graphcomponent ag arg pm]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        model (.getModel graph)
        p (.getDefaultParent graph)]
    (when-let [pmcell (find-premise-cell graph (:id arg) pm)]
      (with-transaction component
        (.remove model pmcell)
        (change-all-cell-and-styles component ag)
        (align-orphan-cells graph p (get-vertices graph p))))))

(defn add-new-premise [graphcomponent ag arg stmt stmt-str]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        model (.getModel graph)
        p (.getDefaultParent graph)]
    (when-let [argcell (find-argument-cell graph (:id arg))]
      (with-transaction component
        (let [x (getx argcell)
              y (gety argcell)
              premise (get-premise (get-argument ag (:id arg)) (statement-atom stmt))
              full (stmt-to-str ag stmt stmt-str)
              stmtcell (insert-vertex graph p (StatementCell. ag stmt stmt-str
                                                              (trunk full) full)
                                      (get-statement-style ag stmt))
              premisescells (map #(find-premise-cell graph (:id arg) %) (:premises arg))]
          (insert-edge graph p (PremiseCell. arg premise) stmtcell argcell
                       (get-edge-style premise))
          (change-all-cell-and-styles component ag)
          (do-layout graph p (get-vertices graph p)))))))

(defn delete-argument [graphcomponent ag arg]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        model (.getModel graph)
        p (.getDefaultParent graph)]
    (when-let [argcell (find-argument-cell graph (:id arg))]
      (with-transaction component
        (.removeCells graph (into-array [argcell]) true)
        (change-all-cell-and-styles component ag)
        (align-orphan-cells graph p (get-vertices graph p))))))

(defn delete-statement [graphcomponent ag stmt]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        model (.getModel graph)
        p (.getDefaultParent graph)]
    (when-let [stmtcell (find-statement-cell graph stmt)]
      (with-transaction component
        (doseq [argid (:conclusion-of (get-node (:ag (.getValue stmtcell)) stmt))]
          (let [argcell (find-argument-cell graph argid)]
            (.removeCells graph (into-array [argcell]) true)))
        (.removeCells graph (into-array [stmtcell]) true)
        (change-all-cell-and-styles component ag)
        (align-orphan-cells graph p (get-vertices graph p))))))

(defn change-mainissue [graphcomponent ag stmt]
  (let [component (:component graphcomponent)]
    (with-transaction component
      (change-all-cell-and-styles component ag))))

(defn add-new-statement [graphcomponent ag stmt stmt-str]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        model (.getModel graph)
        p (.getDefaultParent graph)]
    (with-transaction component
      (change-all-cell-and-styles component ag)
      (insert-vertex graph p (let [full (stmt-to-str ag stmt stmt-str)]
                               (StatementCell. ag stmt stmt-str
                                               (trunk full) full))
                   (get-statement-style ag stmt))
      (align-orphan-cells graph p (get-vertices graph p)))))

(defn add-new-argument [graphcomponent ag arg]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        model (.getModel graph)
        p (.getDefaultParent graph)]
    (with-transaction component
      (change-all-cell-and-styles component ag)
      (let [argvertex (add-argument-vertex graph p ag arg)
            stmtcell (find-statement-cell graph (:conclusion arg))]
        (insert-edge graph p (ArgumentCell. arg) argvertex
                     stmtcell
                     (get-conclusion-edge-style arg)))
      (do-layout graph p (get-vertices graph p)))))

(defn replace-graph [graphcomponent ag stmt-fmt]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        p (.getDefaultParent graph)]
    (with-transaction component
      (.removeCells graph (into-array (get-vertices graph p)) true)
      (fill-graph graph p ag stmt-fmt))))

(defn change-statement-content [graphcomponent ag stmt-fmt oldstmt newstmt]
  (let [component (:component graphcomponent)
        graph (.getGraph component)
        cell (find-statement-cell graph oldstmt)
        stmt-str (:stmt-str (.getValue cell))
        full (stmt-to-str ag newstmt stmt-str)
        stmt (StatementCell. ag newstmt stmt-str
                             (trunk full) full)
        p (.getDefaultParent graph)
        model (.getModel graph)]
    (with-transaction component
      (.setValue model cell stmt)
      (.setStyle model cell (get-statement-style ag newstmt))
      (adjust-size graph cell)
      ;; changing a statement means changing the premises
      ;; of an argument. The cell of premises links has
      ;; to be updated.
      (replace-graph graphcomponent ag stmt-fmt))))