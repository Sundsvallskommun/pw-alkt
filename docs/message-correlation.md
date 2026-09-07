# Message correlation

An incoming event has to wake the process that is already running for an errand. `POST /message` with the errand id as `businessKey` does that, so pw never has to know or store a process instance id. See [operaton-error-decoding.md](operaton-error-decoding.md) for how the failures of this call are read.

`OperatonMapper.toCorrelationMessageDto` builds the body. It sets `all` to false, and that is the only place in the service that decides the flag.

## Why all is false

With `all` on, one message correlates to every execution that matches. With it off, Operaton answers 400 unless exactly one execution matches.

Off is what we want, because the process models are not allowed to wait for the same message on parallel branches. If that rule is ever broken, `all = true` would deliver the message to every branch and the process would carry on looking healthy. `all = false` turns the same situation into a visible error.

## The same 400 means two different things

`POST /message` answers 400 both when the message matched no execution and when it matched several. Both carry the same `type`, `MismatchingMessageCorrelationException`. Only the text in `message` separates them, which is why the Feign error decoder is configured to read that field.

No match is the ordinary case. A process that has finished, or one waiting somewhere else, simply does not take the message. It deserves an information line and an accepted response, not a redelivery: retrying cannot change the outcome and only fills the queue with failures that were never failures.

Several matches is the modelling rule being broken. That deserves an error.

Error handling has to tell them apart. Treat an unrecognised text as the second case, so a real problem is never logged as routine.

## Signals correlate on their own name

When the event subtype is `SIGNAL`, a caseworker is stepping the process forward by hand, and the signal's own name is what gets correlated. `errandUpdated` is the generic message for everything else. Picking between the two is service logic, so the name lands with that code rather than being declared ahead of it.

A signal name that matches nothing the process is waiting for is an ordinary missed correlation. Same handling as above.
