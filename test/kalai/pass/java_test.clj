(ns kalai.pass.java-test
  (:require [clojure.test :refer [deftest testing is]]
            [kalai.pass.test-helpers :refer [top-level-form inner-form]]))

(deftest t1
  (top-level-form
    '(def ^{:t "int"} x 3)
    ;;->
    '(init false "int" x 3)
    ;;->
    "int x = 3;"))

(deftest t15
  (top-level-form
    '(def ^Integer x)
    ;;->
    '(init false Integer x)
    ;;->
    "Integer x;"))

(deftest t16
  (top-level-form
    '(defn f ^Integer [^Integer x]
       (inc x))
    ;;->
    '(function f Integer nil [x]
               (return (operator + x 1)))
    ;;->
    "public static Integer f(Integer x) {
return (x + 1);
}"))

(deftest t17
  (top-level-form
    '(defn f []
       (let [^int x (atom 0)]
         (swap! x inc)))
    ;;->
    '(function f nil nil []
       (do
         (init true int x 0)
         (group
           (assign x (invoke inc x))
           (return x))))
    ;;->
    "public static  f() {
int x = 0;
x = inc(x);
return x;
}"))

(deftest t165
  (top-level-form
    '(defn f
       (^int [^int x]
        (inc x))
       (^int [^int x ^int y]
        (+ x y)))
    ;;->
    '(function f int nil [x]
               (return (operator + x 1)))
    ;;->
    "public static int f(int x) {
return (x + 1);
}
public static int f(int x, int y) {
return (x + y);
}"))

;; Tim proposes: let's not support void!!!
;; It's not necessary for writing programs... returning null is equivalent.
;; If you want to write a side effect function, it should return type Object and return null.
;; That means Kalai can continue to follow the do it the Clojure way mantra.
#_
(deftest t17
  (top-level-form
    '(defn f ^void [^int x]
       (println x))
    ;;->
    '(function f void nil [x]
               (println x))
    ;;->
    "public static void f(int x) {
(x+1);
}"))

(deftest t18
  (inner-form
    '(let [^int x (atom 1)
           ^int y (atom 2)
           ^int z 1]
       (reset! x 3)
       (+ @x (deref y)))
    ;;->
    '(do
       (init true int x 1)
       (init true int y 2)
       (init false int z 1)
       (do
         (assign x 3)
         (operator + x y)))
    ;;->
    "{
int x = 1;
int y = 2;
int z = 1;
{
x = 3;
(x + y);
}
}"))

(deftest t19
  (inner-form
    '(with-local-vars [^int x 1
                       ^int y 2]
       (+ (var-get x) (var-get y)))
    ;;->
    '(do
       (init true int x 1)
       (init true int y 2)
       (operator + x y))
    ;;->
    "{
int x = 1;
int y = 2;
(x + y);
}"))

#_
(deftest t111
  (top-level-form
    '(do (def ^{:kalias '[kmap [klong kstring]]} T)
         (def ^{:t T} x))
    ;;->
    '()
    ;;->
    ""))

;; this test covers type erasure, but we have disabled that
;; as the bottom up traversal does too much (slow)
#_
(deftest t112
  (top-level-form
    '(do (def ^{:t ['kmap ['klong 'kstring]]} x))
    ;;->
    '()
    ;;->
    ""))

(deftest t2
  (inner-form
    '(do (def ^{:t "Boolean"} x true)
         (def ^{:t "Long"} y 5))
    ;;->
    '(do
       (init false "Boolean" x true)
       (init false "Long" y 5))
    ;;->

    ;; TODO: condense could have stripped
    "{
Boolean x = true;
Long y = 5;
}"))

(deftest t3-0
  (inner-form
    '(let [^int x 1]
       x)
    ;;->
    '(do
       (init false int x 1)
       x)
    ;;->
    "{
int x = 1;
x;
}"))

(deftest t3-1
  (inner-form
    '(if true 1 2)
    ;;->
    '(if true 1 2)
    ;;->
    "if (true)
{
1;
}
else
{
2;
}"))

(deftest t3-1-1
  (inner-form
    '(let [^int x (atom 0)]
       (reset! x (+ @x 2)))
    ;;->
    '(do
       (init true int x 0)
       (assign x (operator + x 2)))
    ;;->
    "{
int x = 0;
x = (x + 2);
}"
    ))

(deftest t3-2
  #_(inner-form
    (atom [1 2])
    ;;->
    '(mutable-vector 1 2)
    ;;->
    "PersistentVector tmp1 = new PersistentVector();
tmp1.add(1);
tmp1.add(2);
tmp1;
"))


(deftest t3-2-1
  (inner-form
    [1 2]
    ;;->
    '(persistent-vector 1 2)
    ;;->
    "PersistentVector tmp1 = new PersistentVector();
tmp1.add(1);
tmp1.add(2);
tmp1;"))

(deftest t3-2-2
  (inner-form
    {1 2 3 4}
    ;;->
    '(persistent-map 1 2 3 4)
    ;;->
    "PersistentMap tmp1 = new PersistentMap();
tmp1.put(1, 2);
tmp1.put(3, 4);
tmp1;"))

(deftest t3-2-3
  (inner-form
    #{1 2}
    ;;->
    '(persistent-set 1 2)
    ;;->
    "PersistentSet tmp1 = new PersistentSet();
tmp1.add(1);
tmp1.add(2);
tmp1;"))

(deftest t3-2-1-1
  (inner-form
    '(let [^{:t kvector} x [1 2]]
       (println x))
    ;;->
    '(do
       (init false kvector x
         (persistent-vector 1 2))
       (invoke println x))
    ;;->
    "{
PersistentVector tmp1 = new PersistentVector();
tmp1.add(1);
tmp1.add(2);
kvector x = tmp1;
System.out.println(x);
}"))

(deftest t3-2-1-1-1
  (inner-form
    '(let [^{:t kvector} x [1 [2]]]
       (println x))
    ;;->
    '(do
       (init false kvector x
             (persistent-vector 1 (persistent-vector 2)))
       (invoke println x))
    ;;->
    "{
PersistentVector tmp1 = new PersistentVector();
tmp1.add(1);
PersistentVector tmp2 = new PersistentVector();
tmp2.add(2);
tmp1.add(tmp2);
kvector x = tmp1;
System.out.println(x);
}"))

(deftest t3-2-1-1-1-1
  (inner-form
    '(let [^{:t kvector} x [1 [2] 3 [[4]]]]
       (println x))
    ;;->
    '(do
       (init false kvector x
         (persistent-vector 1
           (persistent-vector 2)
           3
           (persistent-vector
             (persistent-vector 4))))
       (invoke println x))
    ;;->
    "{
PersistentVector tmp1 = new PersistentVector();
tmp1.add(1);
PersistentVector tmp2 = new PersistentVector();
tmp2.add(2);
tmp1.add(tmp2);
tmp1.add(3);
PersistentVector tmp3 = new PersistentVector();
PersistentVector tmp4 = new PersistentVector();
tmp4.add(4);
tmp3.add(tmp4);
tmp1.add(tmp3);
kvector x = tmp1;
System.out.println(x);
}"))

(deftest t3-2-1-1-1-1-2
  (inner-form
    '(let [^{:t kvector} x {1 [{2 3} #{4 [5 6]}]}]
       (println x))
    ;;->
    '(do
       (init false kvector x
         (persistent-map 1
           (persistent-vector
             (persistent-map 2 3)
             (persistent-set
               4
               (persistent-vector 5 6)))))
       (invoke println x))
    ;;->
    "{
PersistentMap tmp1 = new PersistentMap();
PersistentVector tmp2 = new PersistentVector();
PersistentMap tmp3 = new PersistentMap();
tmp3.put(2, 3);
tmp2.add(tmp3);
PersistentSet tmp4 = new PersistentSet();
tmp4.add(4);
PersistentVector tmp5 = new PersistentVector();
tmp5.add(5);
tmp5.add(6);
tmp4.add(tmp5);
tmp2.add(tmp4);
tmp1.put(1, tmp2);
kvector x = tmp1;
System.out.println(x);
}"))

(deftest t4
  (inner-form
    '(doseq [^int x [1 2 3 4]]
       (println x))
    ;;->
    '(foreach int x (persistent-vector 1 2 3 4)
              (invoke println x))
    ;;->
    "PersistentVector tmp1 = new PersistentVector();
tmp1.add(1);
tmp1.add(2);
tmp1.add(3);
tmp1.add(4);
for (int x : tmp1) {
System.out.println(x);
}"))

(deftest t5
  (inner-form
    '(dotimes [x 5]
       (println x))
    ;;->
    '(group
       (init true int x 0)
       (while (operator < x 5)
         (invoke println x)
         (assign x (operator + x 1))))
    ;;->
    "int x = 0;
while ((x < 5)) {
System.out.println(x);
x = (x + 1);
}"))

(deftest t3
  (inner-form
    '(while true
       (println "hi"))
    ;;->
    '(while true
       (invoke println "hi"))
    ;;->
    "while (true) {
System.out.println(\"hi\");
}"))

(deftest test6
  (inner-form
    '(cond true 1
           false 2
           :else 3)
    ;;->
    '(if true
       1
       (if false
         2
         (if :else
           3)))
    ;;->
    "if (true)
{
1;
}
else
{
if (false)
{
2;
}
else
{
if (\":else\")
{
3;
}
}
}"))

(deftest test6*
  (inner-form
    '(:k {:k 1})
    ;;->
    '(method get (persistent-map :k 1) :k)
    ;;->
    "PersistentMap tmp1 = new PersistentMap();
tmp1.put(\":k\", 1);
tmp1.get(\":k\");"))

(deftest test7
  ;; TODO: fix case data literals
  #_(inner-form
    '(case 1
       1 :a
       2 :b)
    ;;->
    '(case 1 {1 [1 :a]
              2 [2 :b]})
    ;;->
    "switch (1) {
case 1 : \":a\";
break;
case 2 : \":b\";
break;
}"))

(deftest test75
  #_(inner-form
      '(def ^{:t [String String]} x {:a "asdf"})
      ;;->
      (init false [String String] x
            (hash-map :a "asdf"))
      ;;->
      "x = new HashMap<String,String>();
  x.add(\":a\", \"asdf\""))

(deftest test8
  #_(inner-form
      '(assoc {:a 1} :b 2)
      ;;->
      ""))

(deftest ternary
  (inner-form
    '(+ (if true 1 2)
        (if true
          (if true 3 4)
          5))
    ;;->
    '(operator +
               (if true 1 2)
               (if true
                 (if true 3 4)
                 5))
    ;;->
    "int tmp1;
if (true)
{
tmp1 = 1;
}
else
{
tmp1 = 2;
}
int tmp2;
if (true)
{
int tmp3;
if (true)
{
tmp3 = 3;
}
else
{
tmp3 = 4;
}
{
tmp2 = tmp3;
}
}
else
{
tmp2 = 5;
}
(tmp1 + tmp2);"
    ;;"((true ? 1 : 2) + (true ? (true ? 3 : 4) : 5));"
    ))

(deftest nested-group
  #_(inner-form
    '(+ 1 (swap! x inc))
    ;;->
    '(operator +
               1
               (group (assign x (invoke inc x))
                      x))
    ;;->
    "int x = (x + 1);
(1 + x);"))

(deftest if-expr-do
  (inner-form
    '(+ (if true (do (println 1) 2)) 4)
    ;;->
    '(operator +
       (if true
         (do
           (invoke println 1)
           2))
       4)
    ;;->
    "int tmp1;
if (true)
{
System.out.println(1);
{
tmp1 = 2;
}
}
(tmp1 + 4);"))


(deftest if-expr-do-2
  (inner-form
    '(+ ^int (if true 1 ^int (if false 2 3)) 4)
    ;;->
    '(operator +
               (if true 1 (if false 2 3))
               4)
    ;;->

    "int tmp1;
if (true)
{
tmp1 = 1;
}
else
{
int tmp2;
if (false)
{
tmp2 = 2;
}
else
{
tmp2 = 3;
}
{
tmp1 = tmp2;
}
}
(tmp1 + 4);"))

(deftest if-expr-do-2-2
  (inner-form
    '(+ ^int (if true 1 ^int (if false 2 [3])) 4)
    ;;->
    '(operator +
               (if true 1 (if false 2 (persistent-vector 3)))
               4)
    ;;->

    "int tmp1;
if (true)
{
tmp1 = 1;
}
else
{
int tmp2;
if (false)
{
tmp2 = 2;
}
else
{
PersistentVector tmp3 = new PersistentVector();
tmp3.add(3);
{
tmp2 = tmp3;
}
}
{
tmp1 = tmp2;
}
}
(tmp1 + 4);"))