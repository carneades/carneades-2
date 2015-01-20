;;; Copyright © 2010 Fraunhofer Gesellschaft 
;;; Licensed under the EUPL V.1.1

(ns carneades.engine.test-statement
  (:use clojure.test
        carneades.engine.statement))

(deftest test-variable?
  (is (variable? '?p))
  (is (variable? '?r1))
  (is (not (variable? 'r2)))
  (is (not (variable? "'?r1"))))

(deftest test-constant?
  (is (constant? 42))
  (is (constant? "plato"))
  (is (constant? (symbol "xyz")))
  (is (not (constant? '?r1))))

(deftest test-statement-complement?
  (is (= '(not (mother Tom Garcia))
         (statement-complement '(mother Tom Garcia))))
  (is (= '(mother Tom Garcia)
         (statement-complement '(not (mother Tom Garcia))))))

(deftest test-statement-atom
  (is (= '(x ?r1 ?r2) (statement-atom '(x ?r1 ?r2))))
  (is (= '(x ?r1 ?r2) (statement-atom '(not (x ?r1 ?r2))))))

(deftest test-statement-formatted
  (is (= "The mother of Tom is Garcia"
         (statement-formatted
          (struct fatom "The mother of %s is %s" '(mother Tom Garcia))))))

(deftest test-statement=
  (is (statement= '(mother Tom Garcia) '(mother Tom Garcia)))
  (is (not (statement= '(mother Tom Garcia) '(father Tom Garcia))))
  (is (statement=
       '(mother Tom Garcia)
       (struct fatom "%s is the mother of %s" '(mother Tom Garcia))))
  (is (statement=
       '(mother Tom Garcia)
       (struct fatom "%s ist die Mutter von %s" '(mother Tom Garcia))))
  (is (not (statement=
        '(mother Tom Garcia)
        (struct fatom "%s is the mother of %s" '(father Tom Garcia)))))
  (is (statement= '(mother '?person '?mother) '(mother '?person '?mother)))
  (is (statement= '(excluded BGB109 (obligated-to-support Joe Sam))
                  `(~'excluded ~'BGB109
                               ~(struct fatom "%s must support %s"
                                        '(obligated-to-support Joe Sam))) )))
