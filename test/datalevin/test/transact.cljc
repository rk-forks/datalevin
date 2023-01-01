(ns datalevin.test.transact
  (:require
   [datalevin.test.core :as tdc :refer [db-fixture]]
   [clojure.test :refer [deftest testing are is use-fixtures]]
   [datalevin.core :as d]
   [datalevin.datom :as dd]
   [datalevin.interpret :as i]
   [datalevin.util :as u]
   [datalevin.constants :as c :refer [tx0]])
  (:import [java.util UUID]))

(use-fixtures :each db-fixture)

(deftest test-auto-update-entity-time
  (let [dir  (u/tmp-dir (str "auto-entity-time-" (random-uuid)))
        conn (d/create-conn dir
                            {:value {:db/valueType :db.type/long}
                             :no    {:db/valueType :db.type/long
                                     :db/unique    :db.unique/identity}}
                            {:auto-entity-time? true})]
    (doseq [n (range 10)]
      (d/transact! conn [{:no n :value n}]))
    (d/transact! conn [{:no 0 :value 10}])
    (let [{:keys [db/created-at db/updated-at]}
          (d/pull (d/db conn) '[*] 1)]
      (is created-at)
      (is updated-at)
      (is (<= created-at updated-at)))
    (let [{:keys [db/created-at db/updated-at]}
          (d/pull (d/db conn) '[*] 2)]
      (is created-at)
      (is updated-at)
      (is (= created-at updated-at)))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-multi-threads-transact
  ;; we serialize writes, so as not to violate uniqueness constraint
  (let [dir  (u/tmp-dir (str "multi-" (random-uuid)))
        conn (d/create-conn
               dir
               {:instance/id
                #:db{:valueType   :db.type/long
                     :unique      :db.unique/identity
                     :cardinality :db.cardinality/one}})]
    (dorun (pmap #(d/transact! conn [{:instance/id %}])
                 (range 2)))
    (let [res (d/q '[:find ?e ?a ?v :where [?e ?a ?v]] @conn)]
      (is (or (= #{[2 :instance/id 0] [1 :instance/id 1]} res)
              (= #{[1 :instance/id 0] [2 :instance/id 1]} res)))
      (is (thrown-with-msg? Exception #"unique constraint"
                            (d/transact! conn [(into [:db/add 3]
                                                     (next (first res)))]))))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-with-1
  (let [dir (u/tmp-dir (str "with-" (random-uuid)))
        db  (-> (d/empty-db dir {:aka {:db/cardinality :db.cardinality/many}})
                (d/db-with [[:db/add 1 :name "Ivan"]])
                (d/db-with [[:db/add 1 :name "Petr"]])
                (d/db-with [[:db/add 1 :aka "Devil"]])
                (d/db-with [[:db/add 1 :aka "Tupen"]]))]

    (is (= (d/q '[:find ?v
                  :where [1 :name ?v]] db)
           #{["Petr"]}))
    (is (= (d/q '[:find ?v
                  :where [1 :aka ?v]] db)
           #{["Devil"] ["Tupen"]}))

    (testing "Retract"
      (let [db (-> db
                   (d/db-with [[:db/retract 1 :name "Petr"]])
                   (d/db-with [[:db/retract 1 :aka "Devil"]]))]

        (is (= (d/q '[:find ?v
                      :where [1 :name ?v]] db)
               #{}))
        (is (= (d/q '[:find ?v
                      :where [1 :aka ?v]] db)
               #{["Tupen"]}))

        (is (= (into {} (d/entity db 1)) {:aka #{"Tupen"}}))))
    (d/close-db db)
    (u/delete-files dir))

  (testing "skipping-nils-in-tx"
    (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
          db  (-> (d/empty-db dir)
                  (d/db-with [[:db/add 1 :attr 2]
                              nil
                              [:db/add 3 :attr 4]]))]
      (is (= [[1 :attr 2], [3 :attr 4]]
             (map (juxt :e :a :v) (d/datoms db :eavt))))
      (d/close-db db)
      (u/delete-files dir))))

(deftest test-with-2
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir {:aka {:db/cardinality :db.cardinality/many}})
                (d/db-with [[:db/add 1 :name "Ivan"]])
                (d/db-with [[:db/add 1 :name "Petr"]])
                (d/db-with [[:db/add 1 :aka "Devil"]])
                (d/db-with [[:db/add 1 :aka "Tupen"]]))]

    (testing "Cannot retract what's not there"
      (let [db (-> db
                   (d/db-with [[:db/retract 1 :name "Ivan"]]))]
        (is (= (d/q '[:find ?v
                      :where [1 :name ?v]] db)
               #{["Petr"]}))))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-with-datoms-1
  (testing "add"
    (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
          db  (-> (d/empty-db dir)
                  (d/db-with [(d/datom 1 :name "Oleg")
                              (d/datom 1 :age 17)
                              [:db/add 1 :aka "x"]]))]
      (is (= #{[1 :age 17]
               [1 :aka "x"]
               [1 :name "Oleg"]}
             (set (map (juxt :e :a :v) (d/datoms db :eavt)))))
      (d/close-db db)
      (u/delete-files dir))))

(deftest test-with-datoms-2
  (testing "retraction"
    (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
          db  (-> (d/empty-db dir)
                  (d/db-with [(d/datom 1 :name "Oleg")
                              (d/datom 1 :age 17)
                              (d/datom 1 :name "Oleg" tx0 false)]))]
      (is (= #{[1 :age 17]}
             (set (map (juxt :e :a :v)
                       (d/datoms db :eavt)))))
      (d/close-db db)
      (u/delete-files dir))))

(deftest test-retract-fns-1
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir {:aka    {:db/cardinality :db.cardinality/many}
                                 :friend {:db/valueType :db.type/ref}})
                (d/db-with [{:db/id 1, :name "Ivan", :age 15, :aka ["X" "Y" "Z"], :friend 2}
                            {:db/id 2, :name "Petr", :age 37}]))]
    (let [db (d/db-with db [[:db.fn/retractEntity 1]])]
      (is (= (d/q '[:find ?a ?v
                    :where [1 ?a ?v]] db)
             #{}))
      (is (= (d/q '[:find ?a ?v
                    :where [2 ?a ?v]] db)
             #{[:name "Petr"] [:age 37]})))

    (is (= (d/db-with db [[:db.fn/retractEntity 1]])
           (d/db-with db [[:db/retractEntity 1]])))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-retract-fns-2
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir {:aka    {:db/cardinality :db.cardinality/many}
                                 :friend {:db/valueType :db.type/ref}})
                (d/db-with [{:db/id 1, :name "Ivan", :age 15, :aka ["X" "Y" "Z"], :friend 2}
                            {:db/id 2, :name "Petr", :age 37}]))]

    (testing "Retract entitiy with incoming refs"
      (is (= (d/q '[:find ?e :where [1 :friend ?e]] db)
             #{[2]}))

      (let [db (d/db-with db [[:db.fn/retractEntity 2]])]
        (is (= (d/q '[:find ?e :where [1 :friend ?e]] db)
               #{}))))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-retract-fns-3
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir {:aka    {:db/cardinality :db.cardinality/many}
                                 :friend {:db/valueType :db.type/ref}})
                (d/db-with [{:db/id 1, :name "Ivan", :age 15, :aka ["X" "Y" "Z"], :friend 2}
                            {:db/id 2, :name "Petr", :age 37}]))]

    (let [db (d/db-with db [[:db.fn/retractAttribute 1 :name]])]
      (is (= (d/q '[:find ?a ?v
                    :where [1 ?a ?v]] db)
             #{[:age 15] [:aka "X"] [:aka "Y"] [:aka "Z"] [:friend 2]}))
      (is (= (d/q '[:find ?a ?v
                    :where [2 ?a ?v]] db)
             #{[:name "Petr"] [:age 37]})))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-retract-fns-4
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir {:aka    {:db/cardinality :db.cardinality/many}
                                 :friend {:db/valueType :db.type/ref}})
                (d/db-with [{:db/id 1, :name "Ivan", :age 15, :aka ["X" "Y" "Z"], :friend 2}
                            {:db/id 2, :name "Petr", :age 37}]))]

    (let [db (d/db-with db [[:db.fn/retractAttribute 1 :aka]])]
      (is (= (d/q '[:find ?a ?v
                    :where [1 ?a ?v]] db)
             #{[:name "Ivan"] [:age 15] [:friend 2]}))
      (is (= (d/q '[:find ?a ?v
                    :where [2 ?a ?v]] db)
             #{[:name "Petr"] [:age 37]})))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-retract-without-value-339-1
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir
                            {:aka    {:db/cardinality :db.cardinality/many}
                             :friend {:db/valueType :db.type/ref}})
                (d/db-with [{:db/id 1,             :name   "Ivan", :age 15,
                             :aka   ["X" "Y" "Z"], :friend 2}
                            {:db/id     2,    :name     "Petr", :age 37
                             :employed? true, :married? false}]))]
    (let [db' (d/db-with db [[:db/retract 1 :name]
                             [:db/retract 1 :aka]
                             [:db/retract 2 :employed?]
                             [:db/retract 2 :married?]])]
      (is (= #{[1 :age 15] [1 :friend 2] [2 :name "Petr"] [2 :age 37]}
             (tdc/all-datoms db'))))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-retract-without-value-339-2
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir
                            {:aka    {:db/cardinality :db.cardinality/many}
                             :friend {:db/valueType :db.type/ref}})
                (d/db-with [{:db/id 1,             :name   "Ivan", :age 15,
                             :aka   ["X" "Y" "Z"], :friend 2}
                            {:db/id     2,    :name     "Petr", :age 37
                             :employed? true, :married? false}]))]
    (let [db' (d/db-with db [[:db/retract 2 :employed? false]])]
      (is (= [(dd/datom 2 :employed? true)]
             (d/datoms db' :eavt 2 :employed?))))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-retract-fns-not-found
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir {:name {:db/unique :db.unique/identity}})
                (d/db-with [[:db/add 1 :name "Ivan"]]))
        all #(vec (d/datoms % :eavt))]
    (are [op] (= [(d/datom 1 :name "Ivan")]
                 (all (d/db-with db [op])))
      [:db/retract 2 :name "Petr"]
      [:db.fn/retractAttribute 2 :name]
      [:db.fn/retractEntity 2]
      [:db/retractEntity 2]
      [:db/retract [:name "Petr"] :name "Petr"]
      [:db.fn/retractAttribute [:name "Petr"] :name]
      [:db.fn/retractEntity [:name "Petr"]])

    (are [op] (= [[] []]
                 [(all (d/db-with db [op]))
                  (all (d/db-with db [op op]))])            ;; idempotency
      [:db/retract 1 :name "Ivan"]
      [:db.fn/retractAttribute 1 :name]
      [:db.fn/retractEntity 1]
      [:db/retractEntity 1]
      [:db/retract [:name "Ivan"] :name "Ivan"]
      [:db.fn/retractAttribute [:name "Ivan"] :name]
      [:db.fn/retractEntity [:name "Ivan"]])
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-transact!
  (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
        conn (d/create-conn dir {:aka {:db/cardinality :db.cardinality/many}})]
    (d/transact! conn [[:db/add 1 :name "Ivan"]])
    (d/transact! conn [[:db/add 1 :name "Petr"]])
    (d/transact! conn [[:db/add 1 :aka "Devil"]])
    (d/transact! conn [[:db/add 1 :aka "Tupen"]])

    (is (= (d/q '[:find ?v
                  :where [1 :name ?v]] @conn)
           #{["Petr"]}))
    (is (= (d/q '[:find ?v
                  :where [1 :aka ?v]] @conn)
           #{["Devil"] ["Tupen"]}))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-transact-compare-different-types
  (testing "different scalars"
    (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
          conn (d/create-conn dir)]
      (d/transact! conn [{:foo 1}
                         {:foo "1"}
                         {:foo :bar}])
      (is (= (d/q '[:find ?v
                    :where
                    [?e :foo ?v]
                    [(= ?v "1")]] @conn)
             #{["1"]}))
      (is (= (d/q '[:find ?v
                    :where
                    [?e :foo ?v]] @conn)
             #{["1"] [:bar] [1]}))
      (d/close conn)
      (u/delete-files dir)))

  (testing "different colls"
    (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
          conn (d/create-conn dir)]
      (d/transact! conn [{:foo [1 2]}
                         {:foo [1 "2"]}
                         {:foo [1 :2]}])
      (is (= (d/q '[:find ?v
                    :where
                    [?e :foo ?v]
                    [(= ?v [1 "2"])]] @conn)
             #{[[1 "2"]]}))
      (is (= (d/q '[:find ?v
                    :where
                    [?e :foo ?v]
                    [(= ?v [1 :2])]] @conn)
             #{[[1 :2]]}))
      (is (= (d/q '[:find ?v
                    :where
                    [?e :foo ?v]] @conn)
             #{[[1 2]] [[1 :2]] [[1 "2"]]}))
      (d/close conn)
      (u/delete-files dir))))

(deftest test-transact!-1
  (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
        conn (d/create-conn dir)]
    (d/transact! conn [{:db/id -1 :a 1}])
    (is (= (d/q '[:find ?v
                  :where [_ :a ?v]] @conn)
           #{[1]}))

    (d/transact! conn [{:db/id -1 :a 2}])
    (is (= (d/q '[:find ?v
                  :where [_ :a ?v]] @conn)
           #{[1] [2]}))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-db-fn-cas
  (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
        conn (d/create-conn dir)]
    (d/transact! conn [[:db/add 1 :weight 200]])
    (d/transact! conn [[:db.fn/cas 1 :weight 200 300]])
    (is (= (:weight (d/entity @conn 1)) 300))
    (d/transact! conn [[:db/cas 1 :weight 300 400]])
    (is (= (:weight (d/entity @conn 1)) 400))
    (is (thrown-msg? ":db.fn/cas failed on datom [1 :weight 400], expected 200"
                     (d/transact! conn [[:db.fn/cas 1 :weight 200 210]])))
    (d/close conn))

  (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
        conn (d/create-conn dir {:label {:db/cardinality :db.cardinality/many}})]
    (d/transact! conn [[:db/add 1 :label :x]])
    (d/transact! conn [[:db/add 1 :label :y]])
    (d/transact! conn [[:db.fn/cas 1 :label :y :z]])
    (is (= (:label (d/entity @conn 1)) #{:x :y :z}))
    (is (thrown-msg? ":db.fn/cas failed on datom [1 :label (:x :y :z)], expected :s"
                     (d/transact! conn [[:db.fn/cas 1 :label :s :t]])))
    (d/close conn)
    (u/delete-files dir))

  (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
        conn (d/create-conn dir)]
    (d/transact! conn [[:db/add 1 :name "Ivan"]])
    (d/transact! conn [[:db.fn/cas 1 :age nil 42]])
    (is (= (:age (d/entity @conn 1)) 42))
    (is (thrown-msg? ":db.fn/cas failed on datom [1 :age 42], expected nil"
                     (d/transact! conn [[:db.fn/cas 1 :age nil 4711]])))
    (d/close conn)
    (u/delete-files dir))

  (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
        conn (d/create-conn dir)]
    (is (thrown-msg? "Can't use tempid in '[:db.fn/cas -1 :attr nil :val]'. Tempids are allowed in :db/add only"
                     (d/transact! conn [[:db/add -1 :name "Ivan"]
                                        [:db.fn/cas -1 :attr nil :val]])))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-db-fn
  (let [dir     (u/tmp-dir (str "skip-" (random-uuid)))
        conn    (d/create-conn dir {:aka {:db/cardinality :db.cardinality/many}})
        inc-age (fn [db name]
                  (if-let [[eid age] (first (d/q '{:find  [?e ?age]
                                                   :in    [$ ?name]
                                                   :where [[?e :name ?name]
                                                           [?e :age ?age]]}
                                                 db name))]
                    [{:db/id eid :age (inc ^long age)} [:db/add eid :had-birthday true]]
                    (throw (ex-info (str "No entity with name: " name) {}))))]
    (d/transact! conn [{:db/id 1 :name "Ivan" :age 31}])
    (d/transact! conn [[:db/add 1 :name "Petr"]])
    (d/transact! conn [[:db/add 1 :aka "Devil"]])
    (d/transact! conn [[:db/add 1 :aka "Tupen"]])
    (is (= (d/q '[:find ?v ?a
                  :where [?e :name ?v]
                  [?e :age ?a]] @conn)
           #{["Petr" 31]}))
    (is (= (d/q '[:find ?v
                  :where [?e :aka ?v]] @conn)
           #{["Devil"] ["Tupen"]}))
    (is (thrown-msg? "No entity with name: Bob"
                     (d/transact! conn [[:db.fn/call inc-age "Bob"]])))
    (let [{:keys [db-after]} (d/transact! conn [[:db.fn/call inc-age "Petr"]])
          e                  (d/entity db-after 1)]
      (is (= (:age e) 32))
      (is (:had-birthday e)))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-db-ident-fn
  (let [dir     (u/tmp-dir (str "skip-" (random-uuid)))
        conn    (d/create-conn dir {:name {:db/unique :db.unique/identity}})
        inc-age (i/inter-fn [db name]
                            (if-some [ent (d/entity db [:name name])]
                              [{:db/id (:db/id ent)
                                :age   (inc ^long (:age ent))}
                               [:db/add (:db/id ent) :had-birthday true]]
                              (throw (ex-info (str "No entity with name: " name) {}))))]
    (d/transact! conn [{:db/id    1
                        :name     "Petr"
                        :age      31
                        :db/ident :Petr}
                       {:db/ident :inc-age
                        :db/fn    inc-age}])
    (is (thrown-msg? "Can’t find entity for transaction fn :unknown-fn"
                     (d/transact! conn [[:unknown-fn]])))
    (is (thrown-msg? "Entity :Petr expected to have :db/fn attribute with fn? value"
                     (d/transact! conn [[:Petr]])))
    (is (thrown-msg? "No entity with name: Bob"
                     (d/transact! conn [[:inc-age "Bob"]])))
    (d/transact! conn [[:inc-age "Petr"]])
    (let [e (d/entity @conn 1)]
      (is (= (:age e) 32))
      (is (:had-birthday e)))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-resolve-eid
  (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
        conn (d/create-conn dir)
        t1   (d/transact! conn [[:db/add -1 :name "Ivan"]
                                [:db/add -1 :age 19]
                                [:db/add -2 :name "Petr"]
                                [:db/add -2 :age 22]])
        t2   (d/transact! conn [[:db/add "Serg" :name "Sergey"]
                                [:db/add "Serg" :age 30]])]
    (is (= (:tempids t1) {-1 1, -2 2, :db/current-tx (+ tx0 1)}))
    (is (= (:tempids t2) {"Serg" 3, :db/current-tx (+ tx0 2)}))
    (is (= #{[1 "Ivan" 19 tx0]
             [2 "Petr" 22 tx0]
             [3 "Sergey" 30 tx0]}
           (d/q '[:find ?e ?n ?a ?t
                  :where [?e :name ?n ?t]
                  [?e :age ?a]] @conn)))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-tempid-ref-295
  (let [dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (-> (d/empty-db dir {:ref {:db/unique    :db.unique/identity
                                       :db/valueType :db.type/ref}})
                (d/db-with [[:db/add -1 :name "Ivan"]
                            [:db/add -2 :name "Petr"]
                            [:db/add -1 :ref -2]]))]
    (is (= #{[1 :name "Ivan"]
             [1 :ref 2]
             [2 :name "Petr"]}
           (tdc/all-datoms db)))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-resolve-eid-refs
  (let [dir  (u/tmp-dir (str "resolve-" (random-uuid)))
        conn (d/create-conn
               dir
               {:friend {:db/valueType   :db.type/ref
                         :db/cardinality :db.cardinality/many}})
        tx   (d/transact! conn [{:name   "Sergey"
                                 :friend [-1 -2]}
                                [:db/add -1 :name "Ivan"]
                                [:db/add -2 :name "Petr"]
                                [:db/add "B" :name "Boris"]
                                [:db/add "B" :friend -3]
                                [:db/add -3 :name "Oleg"]
                                [:db/add -3 :friend "B"]])
        q    '[:find ?fn
               :in $ ?n
               :where [?e :name ?n]
               [?e :friend ?fe]
               [?fe :name ?fn]]]
    (is (= (:tempids tx)
           {1 1, -1 2, -2 3, "B" 4, -3 5, :db/current-tx (+ tx0 1)}))
    (is (= (d/q q @conn "Sergey") #{["Ivan"] ["Petr"]}))
    (is (= (d/q q @conn "Boris") #{["Oleg"]}))
    (is (= (d/q q @conn "Oleg") #{["Boris"]}))
    (d/close conn)
    (u/delete-files dir)))

(deftest test-tempid
  (let [dir (u/tmp-dir (str "tempid-" (random-uuid)))
        db  (d/empty-db
              dir
              {:friend {:db/valueType :db.type/ref}
               :comp   {:db/valueType :db.type/ref, :db/isComponent true}
               :multi  {:db/cardinality :db.cardinality/many}})]
    (testing "Unused tempid" ;; #304
      (is (thrown-msg? "Tempids used only as value in transaction: (-2)"
                       (d/db-with db [[:db/add -1 :friend -2]])))
      (is (thrown-msg? "Tempids used only as value in transaction: (-2)"
                       (d/db-with db [{:db/id -1 :friend -2}])))
      (is (thrown-msg? "Tempids used only as value in transaction: (-1)"
                       (d/db-with db [{:db/id -1}
                                      [:db/add -2 :friend -1]])))
      (is (thrown-msg? "Tempids used only as value in transaction: (-1)"
                       (d/db-with db [{:db/id -1 :multi []}
                                      [:db/add -2 :friend -1]]))))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-transient-294
  "db.fn/retractEntity retracts attributes of adjacent entities https://github.com/tonsky/datalevin/issues/294"
  (let [dir    (u/tmp-dir (str "skip-" (random-uuid)))
        db     (reduce #(d/db-with %1 [{:db/id %2 :a1 1 :a2 2 :a3 3}])
                       (d/empty-db dir)
                       (range 1 10))
        report (d/with db [[:db.fn/retractEntity 1]
                           [:db.fn/retractEntity 2]])]
    (is (= [(d/datom 1 :a1 1)
            (d/datom 1 :a2 2)
            (d/datom 1 :a3 3)
            (d/datom 2 :a1 1)
            (d/datom 2 :a2 2)
            (d/datom 2 :a3 3)]
           (:tx-data report)))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-transact-same
  "same data, transacted twice"
  (let [dir1 (u/tmp-dir (str "skip-" (random-uuid)))
        dir2 (u/tmp-dir (str "skip-" (random-uuid)))
        es   [{:db/id -1 :company "IBM" :country "US"}
              {:db/id -2 :company "PwC" :country "Germany"}]
        db1  (d/db-with (d/empty-db dir1) es)
        dts1 (d/datoms db1 :eavt)
        db2  (d/db-with (d/empty-db dir2) es)
        dts2 (d/datoms db2 :eavt)]
    (is (= dts1 dts2))
    (d/close-db db1)
    (d/close-db db2)
    (u/delete-files dir1)
    (u/delete-files dir2)))

(deftest validate-data
  "validate data during transact"
  (let [sc  {:company {:db/valueType :db.type/string}
             :id      {:db/valueType :db.type/uuid}}
        es  [{:db/id -1 :company "IBM" :id "ibm"}
             {:db/id -2 :company "PwC" :id "pwc"}]
        dir (u/tmp-dir (str "skip-" (random-uuid)))
        db  (d/empty-db dir sc {:validate-data? true})]
    (is (thrown? Exception (d/db-with db es)))
    (d/close-db db)
    (u/delete-files dir)))

#?(:clj
   (deftest test-transact-bytes
     "requires comparing byte-arrays"
     (let [schema      {:bytes {:db/valueType :db.type/bytes}}
           byte-arrays (mapv #(.getBytes ^String %) ["foo" "bar" "foo"])]
       (testing "equal bytes"
         (let [dir  (u/tmp-dir (str "skip-" (random-uuid)))
               db   (d/empty-db dir schema)
               ents (mapv (fn [ba] {:bytes ba}) byte-arrays)]
           (is (every? true?
                       (map #(java.util.Arrays/equals ^bytes %1 ^bytes %2)
                            byte-arrays
                            (map :v (:tx-data (d/with db ents))))))
           (d/close-db db)
           (u/delete-files dir))))))


(deftest issue-127-test
  (let [schema {:foo/id    {:db/valueType   :db.type/string
                            :db/cardinality :db.cardinality/one
                            :db/unique      :db.unique/identity}
                :foo/stats {:db/doc "Blob of additional stats"}}
        dir    (u/tmp-dir (str "issue-127-" (random-uuid)))
        conn   (d/create-conn dir schema)]
    (d/transact! conn [{:foo/id "foo" :foo/stats {:lul "bar"}}])
    (dotimes [n 10000]
      (d/transact! conn [{:foo/id (str "foo" n) :foo/stats {:lul "bar"}}]))
    (is (= 10001 (count (d/q '[:find ?e :where [?e :foo/id _]] @conn))))
    (d/close conn)
    (u/delete-files dir)))
