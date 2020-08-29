(ns kalai.pass.java4-string
  (:require [meander.strategy.epsilon :as s]
            [meander.epsilon :as m]
            [clojure.string :as str]
            [clojure.pprint :as pprint]))

(declare stringify)

;;;; These are helpers

(defn- parens [x]
  (str "(" x ")"))

(defn- param-list [params]
  (parens (str/join ", " params)))

(defn- space-separated [& xs]
  (str/join " " xs))

(defn- line-separated [& xs]
  (str/join \newline xs))

(defn statement [s]
  (str s ";"))


;;;; These are what our symbols should resolve to

(defn expression-statement-str [x]
  (statement (stringify x)))

(defn block-str [& xs]
  (line-separated
    "{"
    (apply line-separated (map stringify xs))
    "}"))

(defn assign-str [variable-name value]
  (statement (str variable-name "=" (stringify value))))

(defn init-str
  ([type variable-name]
   (statement (space-separated type variable-name)))
  ([type variable-name value]
   (statement (space-separated type variable-name "=" (stringify value)))))

#_(defn const [bindings]
    (str "const" Type x "=" initialValue))

#_(defn test* [x]
    ;; could be a boolean expression
    (str x "==" y)
    ;; or just a value
    (str x))


#_(defn conditional [test then else])

(defn invoke-str [function-name args]
  (str function-name (param-list (map stringify args))))

(defn function-str [return-type name doc params body]
  (str
    (space-separated 'public 'static
                     return-type
                     name)
    (space-separated (param-list (for [param params]
                                   (str (or (some-> param meta :tag)
                                            "var")
                                        " "
                                        param)))
                     (stringify body))))

(defn operator-str [op & xs]
  (parens
    (str/join op (map stringify xs))))

(defn class-str [ns-name body]
  (let [parts (str/split (str ns-name) #"\.")
        package-name (str/join "." (butlast parts))
        class-name (last parts)]
    (line-separated
      (statement (space-separated 'package package-name))
      (space-separated 'public 'class class-name
                       (stringify body)))))

(defn return-str [x]
  (space-separated 'return (stringify x)))

(defn while-str [condition body]
  (space-separated 'while (parens (stringify condition))
                   (stringify body)))

;; TODO



(defn foreach-str [sym-type sym xs body]
  (space-separated 'for (parens (space-separated sym-type sym ":" xs))
                   (stringify body)))

(defn for-str [initialization termination increment body]
  (space-separated 'for (parens (str/join "; " (map stringify [initialization termination increment])))
                   (stringify body)))

(defn if-str
  ([test then]
   (line-separated
     (space-separated 'if (parens (stringify test)))
     (stringify then)))
  ([test then else]
   (line-separated
     (space-separated 'if (parens (stringify test)))
     (stringify then)
     'else
     (stringify else))))

(defn ternary-str
  [test then else]
  (space-separated (parens (stringify test))
                   "?" (stringify then)
                   ":" (stringify else)))

(defn switch-str
  [x clauses]
  (space-separated 'switch (parens (stringify x))
                   (stringify clauses)))

(defn case-str [x then]
  (str (space-separated "case" (stringify x) ":" (stringify then))
       \newline "break;"))

;;;; This is the main entry point

(def str-fn-map
  {'j/class                class-str
   'j/operator             operator-str
   'j/function             function-str
   'j/invoke               invoke-str
   'j/init                 init-str
   'j/assign               assign-str
   'j/block                block-str
   'j/expression-statement expression-statement-str
   'j/return               return-str
   'j/while                while-str
   'j/for                  for-str
   'j/foreach              foreach-str
   'j/if                   if-str
   'j/ternary              ternary-str
   'j/switch               switch-str
   'j/case                 case-str})

(def stringify
  (s/match
    (?x . !more ... :as ?form)
    (let [f (get str-fn-map ?x)]
      (if f
        (apply f !more)
        (throw (ex-info (str "Missing function: " ?x) {:form ?form}))))

    (m/pred keyword? ?k) (pr-str (str ?k))

    ?else (pr-str ?else)))

(defn stringify-entry [form]
  (try
    (stringify form)
    (catch Exception ex
      (println "Inner form:")
      (pprint/pprint (:form (ex-data ex)))
      (println "Outer form:")
      (pprint/pprint form)
      (throw ex))))