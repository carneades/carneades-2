;;; Copyright © 2010 Fraunhofer Gesellschaft 
;;; Licensed under the EUPL V.1.1

(ns ^{:doc "Implementation of the Instantiate Scheme assistant"}
  carneades.editor.controller.handlers.instantiatescheme-wizard
  (:use clojure.contrib.def
        carneades.editor.utils.state
        (carneades.engine statement argument lkif)
        carneades.editor.utils.core
        [carneades.engine.unify :only (unify apply-substitution)]
        carneades.editor.controller.handlers.messages
        clojure.contrib.pprint
        (carneades.editor.view wizardsprotocol viewprotocol swinguiprotocol)
        carneades.editor.controller.documents
        carneades.editor.model.properties
        carneades.editor.controller.handlers.suggestions)
  (:require [clojure.string :as str]
            [carneades.engine.owl :as owl]))

(defvar *clauses-id* (str (gensym "clauses-panel-id")))

(defrecord InstantiateSchemeState [view path id
                                   conclusion filter-text conclusion-matches clauses
                                   literal-wizards formulars current-substitution
                                   suggestions form literal vars])

(defn create-instantiatescheme-state [view path id conclusion]
  (InstantiateSchemeState. view path id conclusion nil false nil {} {} {}
                           nil nil nil nil))

(defn- rules-name [rules]
  (map #(str (:id %)) rules))

(defn- get-unifiable-rules [rules conclusion]
  (if conclusion
    (filter #(unify (first (:head %)) conclusion) rules)
    rules))

(defn- filter-schemes [rules filter-text conclusion conclusionmatches]
  (let [rules (if conclusionmatches
                (get-unifiable-rules rules conclusion)
                rules)
        names (rules-name rules)]
    (if (empty? filter-text)
      names
      (let [filter-text (str/lower-case filter-text)]
        (filter #(.contains (str/lower-case %) filter-text) names)))))

(defn on-schemes-panel [state]
  (let [{:keys [view path conclusion rules conclusion-matches filter-text]} state
        names (filter-schemes rules filter-text conclusion conclusion-matches)]
    (display-schemes view names)
    state))

(defn on-pre-instantiatescheme-wizard [state]
  (let [{:keys [view path id conclusion]} state
        scheme-pathname (get-property *argumentation-scheme-file*)]
    (if (empty? scheme-pathname)
      (do
        (display-error view *open-error* *no-scheme-file*)
        nil)
      (try
        (let [content (import-lkif scheme-pathname)
              rules (:rules (:rb content))
              reasoners (get-reasoners path)
              state (assoc state :rules rules :reasoners reasoners)]
          (if (nil? conclusion)
            (do
              (set-conclusion-statement view "")
              (set-conclusionmatches-button-enabled view false)
              state)
            (do
              (set-conclusion-statement view (statement-formatted conclusion))
              (set-conclusionmatches-button-enabled view true)
              state)))
        (catch IllegalArgumentException
            e (display-error view *open-error* (str (format *cannot-open* scheme-pathname)
                                                    ".\n" *invalid-content* ": " (.getMessage e)))
            nil)
        (catch java.io.FileNotFoundException
            e (display-error view *open-error* (str (format *cannot-open* scheme-pathname)
                                                    ".\n " (.getMessage e)))
            nil)
        (catch java.io.IOException
            e (display-error view *open-error* (str (format *cannot-open* scheme-pathname)
                                                    ".\n" *invalid-content* ": " (.getMessage e)))
            nil)
        (catch org.xml.sax.SAXException
            e (display-error view *open-error* (str (format *cannot-open* scheme-pathname)
                                                    ".\n" *invalid-content*))
            nil)))))

(defn on-filter-schemes [state]
  (let [{:keys [view path id filter-text conclusion conclusionmatches rules]} state]
    (let [text (str/lower-case (str/trim filter-text))
          names (filter-schemes rules filter-text conclusion
                                conclusionmatches)]
      (display-schemes view names))
    state))

(defn schemes-panel-validator [settings]
  (let [scheme (get settings "scheme")]
    (when (nil? scheme)
      *select-a-scheme*)))

(defn on-conclusionmatches [state]
  (let [{:keys [view path conclusion conclusion-matches filter-text rules]} state
        names (filter-schemes rules filter-text
                              conclusion conclusion-matches)]
    (display-schemes view names)
    state))

(defn get-rule [scheme rules]
  (first (filter #(= (str (:id %)) scheme) rules)))

(defn on-clauses-panel [state]
  (let [{:keys [settings view path conclusion conclusion-matches rules]} state]
    (let [rule (get-rule (get settings "scheme") rules) 
          clauses (:body rule)
          clause (first clauses)
          nb-clauses (count clauses)
          current-substitution (if conclusion-matches
                                 (unify (first (:head rule)) conclusion)
                                 {})
          clauses {:clauses (apply vector clauses) :index 0 :nclauses nb-clauses}]
      (display-clause view clause 0 nb-clauses statement-formatted)
      (assoc state
        :literal-wizards {}
        :formulars {}
        :current-substitution current-substitution
        :clauses clauses))))

(defn on-previous-clause-button-listener [state]
  (let [{:keys [view clauses]} state
        {:keys [clauses index nclauses]} clauses]
    (when (pos? index)
      (let [index (dec index)
            clause (get clauses index)
            clauses (assoc clauses :index index)]
        (display-clause view clause index nclauses statement-formatted)
        (assoc state :clauses clauses)))))

(defn on-next-clause-button-listener [state]
  (let [{:keys [view clauses]} state
        {:keys [clauses index nclauses]} clauses]
    (when (< index (dec nclauses))
      (let [index (inc index)
            clause (get clauses index)
            clauses (assoc clauses :index index)]
        (display-clause view clause index nclauses statement-formatted)
        (assoc state :clauses clauses)))))

(defn gen-form-id [clause-number literal-nb]
  (str clause-number "-" (gensym literal-nb)))

(defn form-listener [state]
  (let [{:keys [view formulars current-substitution
                formid val value]} state
                form (get formulars formid)
                value (str-term value)
                current-substitution (if (nil? value)
                                       (dissoc current-substitution val)
                                       (assoc current-substitution val value))]
    (assoc state :current-substitution current-substitution)))

(defn previous-suggestion [state]
  (let [{:keys [view formid formulars suggestions]} state]
    (m-let [form (get formulars formid)
            {:keys [current-idx values size]} suggestions]
      (when (pos? current-idx)
        (let [idx (dec current-idx)
              current (nth values idx)
              suggestions (assoc suggestions :current-idx idx)]
          (display-suggestion view form (statement-formatted current) (inc idx) size)
          (assoc state :suggestions suggestions))))))

(defn next-suggestion [state]
  (let [{:keys [view formid formulars suggestions]} state]
    (m-let [form (get formulars formid)
            {:keys [current-idx values size]} suggestions]
      (when (not= current-idx (dec size))
        (let [idx (inc current-idx)
              current (nth values idx)
              suggestions (assoc suggestions :current-idx idx)]
          (display-suggestion view form (statement-formatted current) (inc idx) size)
          (assoc state :suggestions suggestions))))))

(defn use-suggestion [state]
  (m-let [{:keys [view formid formulars suggestions vars]} state
          form (get formulars formid)
          {:keys [current-idx values]} suggestions
          current (nth values current-idx)
          values (filter (complement variable?) (term-args current))
          formatted-values (map term-str values)
          var-values (partition 2 (interleave vars formatted-values))]
    (fillin-formular view form var-values)
    (update-in state [:current-substitution] merge
               (apply hash-map (interleave vars values)))))

(defn get-literal-formular [state-atom view clause-number literal literal-nb]
  (let [formid (gen-form-id clause-number literal-nb)
        formulars (:formulars (deref state-atom)) ]
    (if-let [panel (get formulars formid)]
      panel
      (let [vars (variables literal)
            form (create-literal-formular view
                                          formid
                                          (statement-formatted literal)
                                          []
                                          vars
                                          (str literal-nb)
                                          {:form-listener
                                           (fn [formid val value]
                                             (swap! state-atom assoc
                                                    :formid formid
                                                    :val val
                                                    :value value)
                                             (state-call form-listener state-atom)
                                             (trigger-formpanel-validator
                                              view
                                              (get (:formulars (deref state-atom)) formid)))
                                           :previous-suggestion-listener
                                           (fn [formid]
                                             (swap! state-atom assoc :formid formid)
                                             (state-call previous-suggestion state-atom)) 
                                           :next-suggestion-listener
                                           (fn [formid]
                                             (swap! state-atom assoc :formid formid)
                                             (state-call next-suggestion state-atom))
                                           :use-suggestion-listener
                                           (fn [formid]
                                             (swap! state-atom assoc :formid formid :vars vars)
                                             (state-call use-suggestion state-atom))}
                                          [])
            formulars (assoc formulars formid form)]
        (swap! state-atom assoc :formulars formulars)
        form))))

(defn get-desc [form idx]
  (format *complete-form-n* idx))

(defn literal-validator [settings current-substitution literal idx]
  (let [variables (filter variable? literal)
        formvariables (keep (fn [var]
                              (let [name (str var "-")
                                    val (get settings name)]
                                (when (not (nil? (str-term val)))
                                  [var val])))
                            variables)
        invalid-vars (some #(and (not (empty? %))
                                 (nil? (str-term %)))
                           (keep #(get settings (str % "-")) variables))
        ;; validator is called before our listener so we need to merge the values
        sub (merge current-substitution (apply hash-map (apply concat formvariables)))]
    (cond invalid-vars
          *invalid-content*

          (ground? (apply-substitution sub literal))
          nil

          :else *fillin-form*)))

(defn unifiable-statements [ag literal current-subs]
  (let [stmts (map node-statement (get-nodes ag))
        suggestions (filter #(unify literal %) stmts)]
    suggestions))

(defn on-literal-panel [state]
  (let [{:keys [view path id form literal settings current-substitution reasoners]} state
        ag (get-ag path id)
        var-values (map (fn [[var values]] [var (term-str values)])
                        current-substitution)]
    (fillin-formular view form var-values)
    (let [suggestions (concat
                       (unifiable-statements ag literal current-substitution)
                       (possible-individuals-statements literal reasoners))
          size (count suggestions)]
      (if (empty? suggestions)
        (display-no-suggestion view form)
        (display-suggestion view form (statement-formatted (first suggestions)) 1 size))
      (if (zero? size)
        (assoc state :suggestions nil)
        (assoc state :suggestions {:current-idx 0 :values suggestions :size size})))))

(defn get-literals-wizard [state-atom view path id clause-number]
  (let [{:keys [literal-wizards clauses]} (deref state-atom)]
    (if-let [wizard (get literal-wizards clause-number)]
      wizard
      (m-let [{:keys [clauses index nclauses]} clauses
              literals (get clauses clause-number)
              forms (map-indexed (fn [idx literal]
                                   (get-literal-formular state-atom view clause-number literal ""))
                                 literals)
              wizard (create-wizard
                      view ""
                      (map-indexed (fn [idx form]
                                     (let [literal (nth literals idx)]
                                       {:panel (:panel form)
                                        :desc (get-desc form (inc idx))
                                        :validator
                                        (fn [settings]
                                          (literal-validator
                                           settings
                                           (:current-substitution (deref state-atom))
                                           literal
                                           idx))
                                        :listener
                                        (fn [settings]
                                          (swap! state-atom assoc
                                                 :form form
                                                 :literal literal
                                                 :settings settings)
                                          (state-call on-literal-panel state-atom))}))
                                   forms)
                      (constantly true)
                      [])
              literal-wizards (assoc literal-wizards clause-number wizard)]
        (swap! state-atom assoc :literal-wizards literal-wizards)
        wizard))))

(defn instantiatescheme-panel-selector [state-atom stepid]
  (let [{:keys [settings view path id]} (deref state-atom)]
    (condp = stepid
        *clauses-id* (let [clause-number (get settings "clause-number")]
                       (if (empty? clause-number)
                         nil
                         (let [wizard
                               (get-literals-wizard
                                state-atom view path id
                                (Integer/parseInt clause-number))]
                           wizard)))
        
        nil)))

(defn instantiate-scheme [view path id scheme conclusion premises]
  (m-let [ag (get-ag path id)
          ag (update-statement ag conclusion)
          pms (map premise premises)
          arg (argument (gen-argument-id ag) :pro conclusion pms scheme)
          ag (assert-argument ag arg)]
    (do-ag-update view [path :ags (:id ag)] ag)
    (graph-changed view path ag statement-formatted)
    (display-statement view path ag conclusion statement-formatted)))

(defn on-post-instantiatescheme-wizard [state]
  (let [{:keys [view path id settings current-substitution rules]} state
        rule (get-rule (get settings "scheme") rules)
        clause-number (Integer/parseInt (get settings "clause-number"))
        clause (nth (:body rule) clause-number)
        clause (map #(apply-substitution current-substitution %) clause)
        head (:head rule)
        head (map #(apply-substitution current-substitution %) head)]
    (instantiate-scheme view path id (str (:id rule)) (first head) clause)))
