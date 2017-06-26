(ns tele.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a]
            [tele.core :refer :all]))


(deftest init!-test
  (testing "Initialize without errors."
    (is (= :pass
           (try (do (init!) :pass)
                (catch Throwable _ :fail))))))


(deftest signal-test

  (testing "Call signal function without errors"
    (is (= :pass
           (try (do (signal (init!) :signal :data) :pass)
                (catch Throwable _ :fail)))))

  (testing "Put a signal on the channel"
    (let [tele (init!)
          s (a/sub (:signal-pub tele) :test (a/chan))
          res (promise)]
      (signal tele :test :data)
      (a/thread (deliver res (a/<!! s)))
      (is (= {:signal :test
              :data :data
              :propagate nil}
             (deref res 100 :timeout))))))


(deftest tune-in-test

  (testing "Send and process signal"
    (let [tele (init!)
          res (promise)]
      (tune-in tele :test #(deliver res %))
      (signal tele :test :data)
      (is (= :data
             (deref res 100 :timeout)))))

  (testing "Propagate after processing"
    (let [tele (init!)
          res (promise)]
      (tune-in tele :test inc)
      (tune-in tele :prop #(deliver res %))
      (signal tele :test 0
              :propagate :prop)
      (is (= 1
             (deref res 100 :timeout)))))

  (testing "Handle errors"
    (let [error (promise)
          tele (init! :err-fn (fn [_] (deliver error :error)))]
      (tune-in tele :test (fn [_] (throw (ex-info "Error!" {}))))
      (signal tele :test :data)
      (is (= :error (deref error 100 :timeout))))))


(deftest tune-nonce-test

  (testing "Send and process signal"
    (let [tele (init!)
          res (tune-nonce tele :test inc)]
      (signal tele :test 0)
      (is (= 1
             (deref res 100 :timeout)))))

  (testing "Propogate after processing"
    (let [tele (init!)
          _ (tune-nonce tele :test inc)
          res (tune-nonce tele :prop inc)]
      (signal tele :test 0
              :propagate :prop)
      (is (= 2
             (deref res 100 :timeout)))))

  (testing "Handle errors"
    (let [error (promise)
          tele (init! :err-fn (fn [_] (deliver error :error)))]
      (tune-nonce tele :test (fn [_] (throw (ex-info "Error!" {}))))
      (signal tele :test :data)
      (is (= :error (deref error 100 :timeout))))))


(deftest signal-await-test

    (testing "Return a map"
      (let [t (init!)]
        (tune-in t :signal (fn [_] (do (signal t :signal-complete :complete) :response)))
        (is (= {:prop {:received :response} :signal-complete :complete}
               (signal-await
                :timeout 100
                (signal t :signal :data
                        :propagate :prop)
                (await :prop [p]
                       {:received p})
                (await :signal-complete [s]
                       s)))))))
