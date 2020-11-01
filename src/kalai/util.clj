(ns kalai.util
  (:require [meander.epsilon :as m]
            [meander.syntax.epsilon :as syntax]
            [meander.match.syntax.epsilon :as match]
            [puget.printer :as puget])
  (:import (clojure.lang IMeta)))

(def c (atom 0))
(defn gensym2 [s]
  (symbol (str s (swap! c inc))))

(defn tmp [type]
  (with-meta (gensym2 "tmp") {:t type}))

;; TODO: simplify this
(defn get-type [expr]
  (let [{:keys [t tag]} (meta expr)]
    (or t
        tag
        (when (and (seq? expr) (seq expr))
          (case (first expr)
            ;; TODO: this suggests we need some type inference
            (j/new) (second expr)
            (j/block j/invoke do if) (get-type (last expr))
            (do
              (println "WARNING: missing type for" (pr-str expr))
              "MISSING_TYPE")))
        (when (not (symbol? expr))
          (type expr))
        (do (println "WARNING: missing type for" (pr-str expr))
            "MISSING_TYPE"))))

(defn tmp-for [expr]
  (tmp (get-type expr)))

(defn spy
  ([x] (spy x nil))
  ([x label]
   (println (str "Spy: " label))
   (doto x puget/cprint)))

(defn match-type? [t x]
  (or
    (some-> x
            meta
            (#(or (= t (:t %))
                  (= t (:tag %)))))
    (= t (type x))))

(m/defsyntax of-type [t x]
  (case (::syntax/phase &env)
    :meander/match
    `(match/pred #(match-type? ~t %) ~x)
    &form))

(m/defsyntax var [v]
  (case (::syntax/phase &env)
    :meander/match
    `(m/app meta {:var ~v})
    &form))

(defn void? [expr]
  (#{:void 'void "void"} (get-type expr)))

(defn maybe-meta-assoc
  "If v is truthy, sets k to v in meta of x"
  ([x k v]
   (if v
     (with-meta x (assoc (meta x) k v))
     x))
  ([x k v & more]
   {:pre [(even? (count more))]}
   (apply maybe-meta-assoc (maybe-meta-assoc x k v) more)))

(defn type-from-meta [x]
  (let [{:keys [t tag]} (meta x)]
    (or t tag)))

;; TODO: may be redundant?
(defn propagate-type [from to]
  (if (and (instance? IMeta to)
           (not (type-from-meta to)))
    (maybe-meta-assoc to :t (if (instance? IMeta from)
                              (type-from-meta from)
                              (type from)))
    to))
