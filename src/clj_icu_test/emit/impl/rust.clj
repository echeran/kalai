(ns clj-icu-test.emit.impl.rust
  (:require [clj-icu-test.common :refer :all]
            [clj-icu-test.emit.interface :as iface :refer :all]
            [clj-icu-test.emit.langs :as l]
            [clj-icu-test.emit.impl.util.rust-util :as rust-util]
            [clj-icu-test.emit.impl.util.java-type-util :as java-type-util]
            [clj-icu-test.emit.impl.util.common-type-util :as common-type-util]
            ;;[clojure.edn :as edn]
            [clojure.string :as string]
            [clojure.tools.analyzer.jvm :as az]))

(defmethod iface/emit-scalar-type ::l/rust
  [ast-opts]
  (let [ast (:ast ast-opts)
        class (or (:return-tag ast) 
                  (-> ast :impl-state :type-class-ast :mtype)
                  (:mtype ast)
                  (:tag ast))]
    (when class
      (cond
        ;; TODO: uncomment the primitive type class code unless and until we want to have
        ;; implicit type signatures applied for bindings in a let block.
        ;; Note: the problem is that the analyzer automatically infers the type of the
        ;; binding symbol in a let binding block in :tag and :o-tag even if there is no
        ;; type hint, whereas it doesn't do so in other places of binding (ex: def)
        ;; (= Long/TYPE class) "long"
        ;; (= Integer/TYPE class) "int"
        ;; (= Character/TYPE class) "char"
        ;; (= Boolean/TYPE class) "boolean"
        
        (= Void/TYPE class) nil ;; void return types for fns do not contribute to method type signature
        
        :else (let [canonical-name (.getCanonicalName class)] 
                (cond                
                  ;; this is to prevent the analyzer from auto-tagging the type classes of
                  ;; symbols in a binding form in a way that is currently being assumed to
                  ;; be unwanted in the emitted output.
                  ;; If we need to actually emit a type signature of Object in the future,
                  ;; we can subclass Object to a custom type (ex: ExplicitlyAnObject.java)
                  ;; and tell users to use that new class in their type hints if they want
                  ;; a type signature of java.lang.Object in emitted Java output.
                  (#{"java.lang.Object"
                     "java.lang.Number"} canonical-name)
                  nil

                  (.startsWith canonical-name "java.lang.")
                  (let [java-cpp-type-map {java.lang.Integer "i32"
                                           int "i32"
                                           java.lang.Long "i64"
                                           long "i64"
                                           java.lang.Float "f32"
                                           java.lang.Double "f64"
                                           java.lang.Boolean "bool"
                                           boolean "bool"
                                           java.lang.String "String"
                                           java.lang.Character "char"
                                           java.lang.StringBuffer "String"}]
                    (when-let [transformed-type (get java-cpp-type-map class)]
                      transformed-type))

                  ;; this when condition prevents Clojure-specific (?) classes like "long",
                  ;; "int", etc. that are automatically tagged by the analyzer on various
                  ;; binding symbols from becoming included in the emitted output.  This
                  ;; means that you need to used the boxed versions in type hints like
                  ;; ^Long, ^Integer, etc. in order to create type signatures in the emitted
                  ;; output.
                  (when (.getPackage class))
                  canonical-name

                  :else
                  nil))))))

(defmethod iface/emit-assignment-scalar-type ::l/rust
  [ast-opts]
  (let [ast (:ast ast-opts)
        type-class-ast (get-assignment-type-class-ast ast-opts)
        complex-expr-opts (let [nested-expr-sub-expr-opts ast-opts
                                ;; expression is nested within AST's :init :expr when calling analyze-ns, and :op = :with-meta
                                ;; but calling analyze returns the expr in AST's :init with corresponding :op
                                assignment-init-expr-opts (if (-> ast-opts :ast :init :expr)
                                                            (-> ast-opts (update-in [:ast] (comp :expr :init)))
                                                            (-> ast-opts (update-in [:ast] :init)))
                                assignment-init-expr-with-type-opts (-> assignment-init-expr-opts 
                                                                        (assoc-in [:impl-state :type-class-ast] type-class-ast))]
                            (cond
                              (-> ast-opts :ast :init) assignment-init-expr-with-type-opts
                              :else nested-expr-sub-expr-opts))
        op-code (:op ast)
        type-class-opts (assoc ast-opts :ast type-class-ast) 
        type-str (emit-type type-class-opts)
        identifier (when-let [identifer-symbol (get-assignment-identifier-symbol ast-opts)]
                     (str identifer-symbol))
        expression (emit complex-expr-opts)
        is-atom (rust-util/is-assignment-expr-atom (:ast complex-expr-opts))
        is-immutable (not is-atom)
        identifier-and-type (if is-immutable
                              (if type-str
                                [(str "let " identifier ":") type-str]
                                [(str "let " identifier)])
                              (if type-str
                                [(str "let mut " identifier ":") type-str]
                                [(str "let mut " identifier)]))
        statement-parts (concat
                         identifier-and-type
                         ["="
                          expression])
        statement-parts-opts (-> ast-opts
                                 (assoc :val statement-parts)
                                 map->AnyValOpts)
        statement (emit-statement statement-parts-opts)]
        statement))

;; defn (functions)

(defmethod iface/emit-defn-arg ::l/rust
  [ast-opts]
  {:pre [(= :binding (-> ast-opts :ast :op))]}
  (let [ast (:ast ast-opts)
        arg-name (-> ast :form name) 
        type-class-opts ast-opts 
        type-str (emit-type type-class-opts)
        identifier-signature-parts [(str arg-name ":")
                                    (str "&" type-str)]
        identifier-signature (->> identifier-signature-parts
                                  (keep identity)
                                  (string/join " "))]
    identifier-signature))

(defmethod iface/emit-defn ::l/rust
  [ast-opts]
  {:pre [(= :def (-> ast-opts :ast :op))]}
  (let [ast (:ast ast-opts)
        fn-name (:name ast)
        fn-ast (:init ast) 
        fn-ast-opts (assoc ast-opts :ast fn-ast)
        fn-return-type (if (-> ast :arglists first meta :mtype) 
                         ;; *** this uses eval *** -- see curlybrace.clj
                         (let [curr-ns (-> fn-ast :env :ns find-ns)
                               metadata-form (-> ast :arglists first meta)
                               metadata-val (binding [*ns* curr-ns]
                                              (eval metadata-form))
                               fn-return-type-opts (map->AstOpts {:ast metadata-val :lang (:lang ast-opts)})
                               return-type (emit-type fn-return-type-opts)]
                           return-type) 
                         (emit-type fn-ast-opts))
        ;; Note: currently not dealing with fn overloading (variadic fns in Clojure),
        ;; so just take the first fn method
        fn-method-first (-> fn-ast
                            :expr
                            :methods first)
        fn-method-first-arg-asts (:params fn-method-first)
        fn-method-first-args (-> (assoc ast-opts :ast fn-method-first-arg-asts)
                                 emit-defn-args)
        ;; Note: excluding the "public" access modifier that you would see in Java b/c
        ;; I assume that this happens normally in C++, at least for methods
        fn-method-first-signature-parts ["pub"
                                         "fn"
                                         (str fn-name "(" fn-method-first-args ")")
                                         (when fn-return-type
                                           "->")
                                         fn-return-type]
        fn-method-first-signature (->> fn-method-first-signature-parts
                                       (keep identity)
                                       (string/join " " ))
        fn-method-first-body-ast (:body fn-method-first)
        fn-method-first-body-strs (indent
                                   (if (:statements fn-method-first-body-ast)
                                     ;; if ast has key nesting of [:body :statements], then we have a multi-"statement" expression do block in the let form
                                     (let [butlast-statements (:statements fn-method-first-body-ast)
                                           last-statement (:ret fn-method-first-body-ast)
                                           statements (concat butlast-statements [last-statement])
                                           statement-ast-opts (map #(assoc ast-opts :ast %) statements)
                                           statement-strs (map emit statement-ast-opts)]
                                       statement-strs)
                                     ;; else the let block has only one "statement" in the do block
                                     [(emit (assoc ast-opts :ast fn-method-first-body-ast))]))
        fn-method-first-body-strs-opts-seq (map #(-> ast-opts
                                                     (assoc :val %)
                                                     map->AnyValOpts)
                                                fn-method-first-body-strs) 
        fn-method-first-body-strs-with-semicolons (indent
                                                   (map #(if-not (can-become-statement %)
                                                           (:val %)
                                                           (emit-statement %))
                                                        fn-method-first-body-strs-opts-seq))
        fn-method-first-body-str (string/join "\n" fn-method-first-body-strs-with-semicolons)
        fn-method-first-str-parts [(str (indent-str-curr-level) fn-method-first-signature)
                                   (str (indent-str-curr-level) "{")
                                   fn-method-first-body-str
                                   (str (indent-str-curr-level) "}")]
        fn-method-first-str (->> fn-method-first-str-parts
                                 (keep identity)
                                 (string/join "\n"))]
    fn-method-first-str))

(defmethod iface/emit-str-arg ::l/rust
  [ast-opts]
  (let [ast (:ast ast-opts)
        tag-class (:tag ast)
        emitted-arg (emit ast-opts)
        tag-class-opts (-> ast-opts
                           (assoc :val tag-class)
                           map->AnyValOpts)
        casted-emitted-arg (if (is-number-type? tag-class-opts)
                             (str "(" emitted-arg ").to_string()") 
                             emitted-arg)]
    casted-emitted-arg))

(defmethod iface/emit-str-args ::l/rust
  [ast-opts]
  (let [ast (:ast ast-opts)
        args-ast (:args ast)
        args-ast-opts (map #(assoc ast-opts :ast %) args-ast)
        emitted-args (map emit-str-arg args-ast-opts)]
    emitted-args))

(defmethod iface/emit-str ::l/rust
  [ast-opts]
  (let [ast (:ast ast-opts)
        arg-strs (emit-str-args ast-opts)
        format-first-arg (->> (concat ["\""]
                                      (-> (count arg-strs)
                                          (repeat "{}"))
                                      ["\""])
                              (apply str))
        format-all-args (concat [format-first-arg]
                                arg-strs)
        format-arg-expr (string/join ", " format-all-args)
        format-expr-parts ["format!("
                           format-arg-expr
                           ")"]
        expr (apply str format-expr-parts)]
    expr))

(defmethod iface/emit-println ::l/rust
  [ast-opts]
  (let [ast (:ast ast-opts)
        arg-str (emit-str ast-opts)
        all-arg-strs ["println!(\"{}\", "
                      arg-str
                      ")"]
        command-expr (apply str all-arg-strs)]
    command-expr))