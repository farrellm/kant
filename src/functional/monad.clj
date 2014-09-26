(ns functional.monad
  (:require [clojure.core.match :refer [match]]))

(declare <*>)
(declare <*)
(declare >>=)

(defprotocol Pure
  (pure [_ val] "constructor"))

(defprotocol Functor
  (-fmap [v f]))
;; order of parameters in protocol is necessarily reversed since
;; dispatch is on the first argument.
(defn fmap [f v]
  (if (satisfies? Functor v) (-fmap v f)
      (<*> (pure v f) v)))

(defprotocol Applicative
  (-ap [a b] "application"))

(defn <*>
  ([f] (fmap #(%) f))
  ([af av & avs] (cond
                  avs (apply <*> (<* af av) avs)
                  (satisfies? Applicative af) (-ap af av)
                  :else (>>= af (fn [f] (>>= av (fn [v] (pure af (f v)))))))))

(defn <*
  "partial application in an applicative"
  ([af] af)
  ([af a & r] (if r (apply <* (<* af a) r)
                  (<*> (fmap (fn [f] #(partial f %)) af) a))))

(defn m-sequence [x]
  (apply <*> (pure (first x) vector) x))

(defprotocol Category
  (id [_])
  (-comp [_ a]))

(defn <<<
  ([a] a)
  ([a b & c] (if c (-comp a (apply <<< b c))
                 (-comp a b))))

(defn >>>
  ([a] a)
  ([a b & c] (if c (-comp (apply <<< b c) a)
                 (-comp b a))))

(defprotocol Arrow
  (-first [_ a]))

(defn arr-first
  ([arr]   #(-first arr %))
  ([arr a] (-first arr a)))

(defn arr-second
  ([arr]       #(arr-second arr %))
  ([arr [a b]] (let [[c d] (-first arr [b a])]
                 [d c])))

(defn ***
  ([f g]       #(*** f g %))
  ([f g [a b]] (let [[c _] (arr-first f [a nil])
                     [d _] (arr-first g [b nil])]
                 [c d])))

(defn &&&
  ([f g]   #(&&& f g %))
  ([f g a] (let [[c _] (arr-first f [a nil])
                 [d _] (arr-first g [a nil])]
             [c d])))

(defprotocol Monad
  (-bind [m f] "bind"))

(defn >>=
  ([m] m)
  ([m f & fs] (if fs (apply >>= (>>= m f) fs)
                  (-bind m f))))

(defprotocol Monoid
  (zero [_])
  (-plus [a b]))
(defprotocol MonoidSum
  "Optional protocol for more efficient sum"
  (-sum [a as]))

(defn plus [a & as]
  (if (satisfies? MonoidSum a) (-sum a as)
      (reduce -plus a as)))

(defn m-do*
  ([body] (m-do* body false))
  ([body type]
     (match [body]
            [([val] :seq)]       (match val
                                        [:return v] `(pure ~type ~v)
                                        v           v)
            [([fst & rst] :seq)] (match fst
                                        [:let & vs] `(let [~@vs] ~(m-do* rst type))
                                        [:return v] `(>>= (pure ~type ~v) (fn [_#] ~(m-do* rst type)))
                                        [:guard v]  `(>>= (if ~v (pure ~type nil) (zero ~type))
                                                          (fn [_#] ~(m-do* rst type)))
                                        [k v]       (if type
                                                      `(>>= ~v (fn [~k] ~(m-do* rst type)))
                                                      (let [t `t#]
                                                        `(let [~t ~v]
                                                           (>>= ~t (fn [~k] ~(m-do* rst t))))))
                                        [k v & rs]  (m-do* (concat [[k v]] [rs] rst) type)
                                        v           (if type
                                                      `(>>= ~v (fn [_#] ~(m-do* rst type)))
                                                      (let [t `t#]
                                                        `(let [~t ~v]
                                                           (>>= ~t (fn [_#] ~(m-do* rst t))))))))))

(defmacro m-do [& body]
  (m-do* body))

(defn applicative-fmap [v f]
  (<*> (pure v f) v))

(defn monad-<*> [af av]
  (m-do [f af]
        [v av]
        (pure af (f v))))

(defn lift [f]
  (fn [& m-args] (m-do [args (m-sequence m-args)]
                       [:return (apply f args)])))

(defn join [m]
  (>>= m identity))

(defn >=>
  ([f] f)
  ([f & fs] #(apply >>= (f %) fs)))
