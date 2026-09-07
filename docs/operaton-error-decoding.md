# Operaton error decoding

Operaton reports failures with its own `ExceptionDto`, not with RFC 9457:

```json
{
  "type": "MismatchingMessageCorrelationException",
  "message": "Cannot correlate message 'errandUpdated': No process definition or execution matches the parameters"
}
```

## Why the standard decoder does not fit

dept44's `ProblemErrorDecoder` deserializes the body into `DefaultProblemResponse`, which knows `type`, `title`, `status`, `detail` and `instance`. There is no `message` field, and Jackson ignores unknown properties, so nothing fails. The engine's own text is dropped without a trace and callers see:

```
Bad Gateway: operaton error: {status=400 Bad Request}
```

## Why that text matters

`POST /message` answers 400 in two situations that mean opposite things. The message matched no execution, or it matched several. Both carry the same `type`, `MismatchingMessageCorrelationException`, so only the text tells them apart.

No match is the routine case and belongs at INFO. Several matches means the modelling rule about parallel branches is broken and belongs at ERROR. Without the text pw cannot make that call.

## What we use

`JsonPathErrorDecoder` from dept44, with `$.type` as title and `$.message` as detail. It exists for JSON errors that do not follow RFC 9457, so we configure it rather than write anything.

We deliberately did not write our own decoder. `AbstractErrorDecoder.ErrorMessage` and its factory methods are package private, so a decoder living in `se.sundsvall.alkt` would have to format the error string by hand and would drift away from how every other integration reports failures.

Error types are unchanged. 4xx still becomes `ClientProblem`, 5xx still becomes `ServerProblem`, both with status `BAD_GATEWAY`. No bypass codes are configured.

## Support Management is different on purpose

`SupportManagementConfiguration` keeps `ProblemErrorDecoder`. Support Management is a dept44 service and answers with RFC 9457, where that decoder is the right one. The two integrations differ because the two APIs differ.

## Known limitation

Jayway JsonPath throws when a definite path is absent. A body carrying `type` but no `message` therefore loses both fields and decodes to `title=Unknown error`, with a warning in the log. `OperatonConfigurationTest` pins that behaviour so a dept44 upgrade that changes it shows up.

If Operaton turns out to send such bodies in practice, the way out is a decoder of our own that tolerates missing fields. Do not build it before that happens.
