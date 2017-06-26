(ns tele.core
  (:require #?(:clj [clojure.core.async :as a]
               :cljs [cljs.core.async :as a])
            [clojure.spec.alpha :as spec]))

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


(spec/def ::signal-form
  (spec/cat :call-fn #{'signal}
            :tele symbol?
            :signal any?
            :data any?
            :opts (spec/* (spec/cat
                           :opt-name #{:propagate}
                           :opt-val any?))))

(spec/def ::await-form
  (spec/cat :call-fn #{'await}
            :signal any?
            :bindings (spec/tuple any?)
            :body (spec/+ any?)))

(spec/def ::signal-await-body
  (spec/cat :opts (spec/* (spec/cat :opt-name #{:timeout}
                                    :opt-val int?))
            :signal-form (spec/spec ::signal-form)
            :await-forms (spec/+ (spec/spec ::await-form))))


(defn ^:private tune-nonce-form
  [tele signal binding body-forms]
  `(tune-nonce ~tele ~signal
               (fn ~binding ~@body-forms)))


(defn ^:private parse-opts
  [opts]
  (->> opts
       (map (juxt :opt-name :opt-val))
       (into {})))


(defn ^:private deref-form
  [sym signal timeout]
  `(let [res# (deref ~sym ~timeout ::timeout)]
     (if (= res# ::timeout)
       (throw (ex-info (str "Timed out awaiting respose for " ~signal)
                       {:signal-name ~signal
                        :timeout ~timeout}))
       res#)))


(spec/fdef signal-await
           :args ::signal-await-body
           :ret map?)

(defmacro signal-await
  [& body]
  (let [{:keys [opts signal-form await-forms]} (spec/conform ::signal-await-body body)
        t (:tele signal-form)
        {:keys [timeout] :or {timeout 10000}} (parse-opts opts)
        await-forms (map #(assoc % :sym (gensym)) await-forms)]
    `(let ~(reduce into [] (for [{:keys [signal bindings body sym]} await-forms]
                         [sym (tune-nonce-form t signal bindings body)]))
       ~(spec/unform ::signal-form signal-form)
       ~(into {} (for [{:keys [sym signal]} await-forms]
                   [signal (deref-form sym signal timeout)])))))


(comment ;; returns (in a promise) a map:
         ;; {:prop {:received p} :signal-complete nil}
  (signal-await
   :timeout 100
   (signal t :signal :data
           :propagate :prop)
   (await :prop [p]
          {:received p})
   (await :signal-complete [s]
          (println s))))

(comment ;; above expands to:
  (let [prop (tune-nonce t :prop (fn [p] {:received p}))
        signal-complete (tune-nonce t :signal-complete (fn [s] (println s)))]
    (signal t :signal :data :propagate :prop)
    {:prop (let [res (deref prop 100 ::timeout)]
             (if (= res ::timeout)
               (throw (ex-info (str "Timed out awaiting respose for " :prop)
                               {:signal-name :prop
                                :timeout 100}))
               res))
     :signal-complete (let [res (deref signal-complete 100 ::timeout)]
                        (if (= res ::timeout)
                          (throw (ex-info (str "Timed out awaiting respose for " :signal-complete)
                                          {:signal-name :signal-complete
                                           :timeout 100}))
                          res))}))
