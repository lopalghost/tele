# tele

Tele is a minimal event-processing library for Clojure. It is a channel-hiding 
abstraction over core.async. Events are called signals, and are processed
asyncronously.

## Basic Usage

    (require '[tele.core :as tele])

Initialize the signaling system:

    (def tele (tele/init!))
    
Set up a listener:

    (tele/tune-in tele
                  :event/success ;; Signal type. Can be any value, but a 
                                  ;; namespaced keyword is recommended!

                  #(println %))   ;; Signal handler
                  
Send a signal:

    (tele/signal tele :event/success "Success!")
    
## Features
    
### Error Handling

`init` takes a single keyword argument, `:err-fn`, a function of two arguements,
to be called if a listener throws an exception:

    (def tele (tele/init! :err-fn (fn [s e] 
                                    (println "Could not process signal:")
                                    (println s)
                                    (println (.getMessage e))
                                    (println e))))
                                    
    (def master-count (atom 0))

    (tele/tune-in tele :event/count #(swap! master-count + (:count/amount %)))
    
    (tele/signal tele :event/count {:count/amount "a"})
    
    ;; Exception info is printed to *out*
    
### Signal Propagation
    
A signal can ask its listeners to propagate their return values:

    (tele/tune-in tele :event/count-update #(println "New count:" %))
    
    (tele/signal tele :event/count {:count/amount 42}
                 :propagate :event/count-update)
                 
    ;; New value of master-count is printed
    
### Await responses synchronously

Signals that don't have listeners are dropped from the channel.
Unfortunately, this means that if we want to send a signal and then react to
another signal in response, we need to set up the listeners before sending the 
signal, which makes our code confusing to read. To assist the user in writing
more synchronous-looking code, the `signal-await` macro is provided:

    (signal-await
      :timeout 100                   ;; timeout is an optional parameter
      (signal t :signal :data        ;; send a signal
                :propagate :prop)
      (await :prop [p]               ;; the *await* form only works withint signal-await
        {:received p})
      (await :signal-complete [s]
        s))
        
    ;; returns {:prop {:received :response} :signal-complete :complete}
    
`signal-await` is synchronous and will block until it receives each signal
it is waiting on. An exception is thrown on timeout (default timeout is 10
seconds).

## License

Copyright © 2017 G Nalven

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
