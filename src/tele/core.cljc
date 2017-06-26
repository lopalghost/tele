(ns tele.core
  (:require #?(:clj [clojure.core.async :as a]
               :cljs [cljs.core.async :as a])))

(def ^:dynamic *buffer-size* 100)

(defn init!
  [& {:keys [err-fn]}]
  (let [signal-chan (a/chan (a/sliding-buffer *buffer-size*))
        signal-pub (a/pub signal-chan :signal)]
    {:signal-chan signal-chan
     :signal-pub signal-pub
     :err-fn (or err-fn identity)}))

(defn kill!
  [tele]
  (a/close! (:signal-chan tele)))

(defn signal
  [t signal data & {:keys [propagate]}]
  (let [s {:signal signal
           :data data
           :propagate propagate}]
    (a/put! (:signal-chan t) s)
    nil))

(defn ^:private sub
  [t signal]
  (a/sub (:signal-pub t) signal (a/chan *buffer-size*)))

(defn ^:private signal-handler
  [t f]
  (fn [{:keys [data propagate] :as x}]
    (try (let [res (f data)]
           (when propagate (signal t propagate res))
           res)
         (catch #?(:clj Throwable :cljs js/Object) e
             ((:err-fn t) x)))))

(defn tune-in
  "Executes a function for each signal received."
  [t signal f]
  (let [xf (map (signal-handler t f))
        signal-sub (sub t signal)]
    (a/transduce xf (constantly nil) nil signal-sub)
    nil))

(defn tune-nonce
  "Executes a function on the first signal received, returning a derefable with
  the result."
  [t signal f]
  (let [res (promise)
        signal-sub (sub t signal)
        handler (signal-handler t f)]
    (a/go (deliver res (handler (a/<! signal-sub))))
    res))

