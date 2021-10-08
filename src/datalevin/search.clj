(ns datalevin.search
  "Fuzzy full-text search engine"
  (:require [datalevin.lmdb :as l]
            [datalevin.util :as u]
            [datalevin.constants :as c]
            [datalevin.bits :as b])
  (:import [datalevin.sm SymSpell Bigram]
           [java.util HashMap ArrayList]))

(if (u/graal?)
  (require 'datalevin.binding.graal)
  (require 'datalevin.binding.java))

(defn en-analyzer
  "English analyzer does the following:
  - split on white space and punctuation, remove them
  - lower-case all characters
  - remove stop words
  Return a list of [term, position, offset]"
  [^String x]
  (let [len   (.length x)
        len-1 (dec len)
        res   (ArrayList.)]
    (loop [i     0
           pos   0
           in?   false
           start 0
           sb    (StringBuilder.)]
      (if (< i len)
        (let [c (.charAt x i)]
          (if (or (Character/isWhitespace c) (c/en-punctuations c))
            (if in?
              (let [word (.toString sb)]
                (when-not (c/en-stop-words word)
                  (.add res [word pos start]))
                (recur (inc i) (inc pos) false i (StringBuilder.)))
              (recur (inc i) pos false i sb))
            (recur (inc i) pos true (if in? start i)
                   (.append sb (Character/toLowerCase c)))))
        (let [c (.charAt x len-1)]
          (if (or (Character/isWhitespace c) (c/en-punctuations c))
            res
            (let [word (.toString sb)]
              (when-not (c/en-stop-words word)
                (.add res [word pos start]))
              res)))))))

(defprotocol ISearchEngine
  (add-doc [this doc-ref doc-text] [this doc-text]
    "Add a document to the search engine, `doc-ref` can be arbitrary data that
     uniquely refers to the document in the system. `doc-text` is the content of
     the document as a string. Return `doc-id`, an auto-increment long
     integer assigned to the document.")
  (remove-doc [this doc-id]
    "Remove a document. `doc-id` is a long, as returned by `add-doc`.")
  (search [this query]
    "Issue a `query` to the search engine. `query` is a map.
     Return a lazy sequence of `[doc-ref doc-id]`, or `[doc-text doc-id]`
     if `doc-ref` is not given when adding the document. These are ordered by
     relevance to the query."))

(defn- collect-terms
  [result]
  (let [terms (HashMap.)]
    (doseq [[term position offset] result]
      (.put terms term (if-let [[^long freq ^ArrayList lst] (.get terms term)]
                         [(inc freq) (do (.add lst [position offset]) lst)]
                         [1 (doto (ArrayList.) (.add [position offset]))])))
    terms))

(defn- collect-bigrams
  [result]
  (let [bigrams (HashMap.)]
    (doseq [[[t1 p1 _] [t2 p2 _]] (partition 2 1 result)]
      (when (= (inc ^long p1) p2)
        (.put bigrams [t1 t2] (if-let [^long freq (.get bigrams [t1 t2])]
                                (inc freq)
                                1))))
    bigrams))

(deftype SearchEngine [lmdb
                       ^HashMap unigrams ; term -> [term-id,freq]
                       ^HashMap bigrams  ; [term-id,term-id] -> freq
                       ^HashMap terms    ; term-id -> term
                       ^SymSpell symspell
                       ^:volatile-mutable ^long max-doc
                       ^:volatile-mutable ^long max-term]
  ISearchEngine
  (add-doc [this doc-ref doc-text]
    (let [result (en-analyzer doc-text)]
      (locking this
        (let [txs    (ArrayList.)
              doc-id (inc max-doc)]
          (set! max-doc doc-id)
          (.add txs [:put c/docs doc-id (or doc-ref doc-text) :id :data])
          (doseq [[term [^long new-freq new-lst]] (collect-terms result)]
            (let [[term-id freq] (if (.containsKey unigrams term)
                                   (let [[t f] (.get unigrams term)]
                                     [t (+ ^long f new-freq)])
                                   (let [t (inc max-term)]
                                     (set! max-term t)
                                     (.put terms t term)
                                     [t new-freq]))]
              (.put unigrams term [term-id freq])
              (.add txs [:put c/unigrams term [term-id freq]
                         :string :double-id])
              (.add txs [:put c/term-docs term-id doc-id :id :id])
              (doseq [po new-lst]
                (.add txs [:put c/positions [doc-id term-id] po
                           :double-id :double-int]))))
          (doseq [[[t1 t2] ^long new-freq] (collect-bigrams result)]
            (let [tids [(first (.get unigrams t1))
                        (first (.get unigrams t2))]
                  freq (if (.containsKey bigrams tids)
                         (+ ^long (.get bigrams tids) new-freq)
                         new-freq)]
              (.put bigrams tids freq)
              (.add txs [:put c/bigrams tids freq :double-id :id])))
          (l/transact-kv lmdb txs))))))

(defn- init-unigrams
  [lmdb]
  (let [unigrams (HashMap.)
        terms    (HashMap.)
        load     (fn [kv]
                   (let [term          (b/read-buffer (l/k kv) :string)
                         [id _ :as tf] (b/read-buffer (l/v kv) :double-id)]
                     (.put unigrams term tf)
                     (.put terms id term)))]
    (l/visit lmdb c/unigrams load [:all] :string)
    [unigrams terms]))

(defn- init-bigrams
  [lmdb]
  (let [m    (HashMap.)
        load (fn [kv]
               (.put m (b/read-buffer (l/k kv) :double-id)
                     (b/read-buffer (l/v kv) :id)))]
    (l/visit lmdb c/bigrams load [:all])
    m))

(defn- init-max-doc
  [lmdb]
  (or (first (l/get-first lmdb c/docs [:all-back] :id :ignore)) 0))

(defn- init-max-term [lmdb]
  (or (first (l/get-first lmdb c/term-docs [:all-back] :id :ignore)) 0))

(defn new-engine
  [lmdb]
  (assert (not (l/closed-kv? lmdb)) "LMDB env is closed.")
  (l/open-dbi lmdb c/unigrams c/+max-key-size+ (* 2 c/+id-bytes+))
  (l/open-dbi lmdb c/bigrams (* 2 c/+id-bytes+) c/+id-bytes+)
  (l/open-dbi lmdb c/docs c/+id-bytes+)
  (l/open-inverted-list lmdb c/term-docs c/+id-bytes+ c/+id-bytes+)
  (l/open-inverted-list lmdb c/positions (* 2 c/+id-bytes+) c/+id-bytes+)
  (l/open-dbi lmdb c/search-meta c/+max-key-size+)
  (let [[unigrams ^HashMap terms] (init-unigrams lmdb)
        bigrams                   (init-bigrams lmdb)

        tf (into {} (map (fn [[t [_ f]]] [t f]) unigrams))
        bg (into {} (map (fn [[[t1 t2] f]]
                           [(Bigram. (.get terms t1) (.get terms t2))
                            f])
                         bigrams))]
    (->SearchEngine lmdb
                    unigrams
                    bigrams
                    terms
                    (SymSpell. tf bg c/dict-max-edit-distance
                               c/dict-prefix-length)
                    (init-max-doc lmdb)
                    (init-max-term lmdb))))

(comment

  (def env (l/open-kv "/tmp/search12"))

  (def engine (new-engine env))

  (add-doc engine 0 "The quick red fox jumped over the lazy red dogs.")

  (add-doc engine 1 "Mary had a little lamb whose fleece was red as fire.")

  (.-unigrams engine)
  {"red" [1 2], "over" [2 1], "quick" [3 1], "lazy" [4 1], "jumped" [5 1], "dogs" [6 1], "fox" [7 1]}
  (.-terms engine)
  {1 "red", 2 "over", 3 "quick", 4 "lazy", 5 "jumped", 6 "dogs", 7 "fox"}
  (.-bigrams engine)

  (l/get-range env c/unigrams [:all] :string :double-id)
  (l/get-range env c/bigrams [:all] :double-id :id)
  (l/get-range env c/docs [:all] :id)
  (l/get-list env c/term-docs 1 :id :id)
  (l/list-count env c/term-docs 1 :id)
  (l/in-list? env c/term-docs 1 2 :id :id)

  (l/get-list env c/positions [1 1] :double-id :double-int)
  (l/list-count env c/positions [1 1] :double-id)

  (def unigrams {"hello" 49 "world" 30})
  (def bigrams {(Bigram. "hello" "world") 30})

  (def sm (time (SymSpell. unigrams bigrams 2 10)))

  (.getDeletes sm)

  (.addBigrams sm {(Bigram. "hello" "world") 3})

  (.getBigramLexicon sm)

  (.addUnigrams sm {"hello" 3 "datalevin" 1})

  (.getUnigramLexicon sm)


  (.lookupCompound sm "hell" 2 false)

  (def lst (l/open-inverted-list env "i" c/+id-bytes+))

  (l/put-list-items lst "a" [1 2 3 4] :string :id)
  (l/put-list-items lst "b" [5 6 7] :string :id)

  (l/list-count lst "a" :string)
  (l/list-count lst "b" :string)

  (l/del-list-items lst "a" :string)

  (l/in-list? lst "b" 7 :string :id)

  (l/get-list lst "a" :string :id)

  (l/close-kv env)

  )