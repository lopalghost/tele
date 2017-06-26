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

  ;; Note! Propogation won't work if you signal then listen for propagation.
  ;; Propagation handling must be set up before sending the signal.
  #_(testing "Propagate after processing"
    (let [tele (init!)
          res (promise)]
      (tune-in tele :test #(deliver res %))

      (signal tele :test :data
              :propagate :prop)
      (is (= :data
             (deref res 100 :timeout)))))

  #_(testing "Handle errors"))
