# Deleting a process instance

When an errand is deleted in Support Management, nothing is left there to point at the process: `errand_process` goes with the errand. If pw does not end the instance, it keeps running in Operaton for an errand that no longer exists, and no one is left who knows about it.

`findProcessInstances` locates the instance by business key. `deleteProcessInstance` ends it.

## There is no reason parameter

The solution document passes a reason along with the delete. The endpoint does not take one.

`DELETE /process-instance/{id}` accepts `skipCustomListeners`, `skipIoMappings`, `skipSubprocesses` and `failIfNotExists`. That is the whole list. `deleteReason` exists only on `POST /process-instance/delete`, the batch endpoint, which takes a list of instance ids, runs asynchronously and returns a Batch to poll. Wrong tool for ending one instance.

So the reason is logged in pw instead of being handed to the engine. Do not "fix" this by moving to the batch endpoint.

## failIfNotExists is false

The same event can be delivered twice. On the second delivery the instance is already gone, and without the flag a request that went exactly as intended would come back as an error.

`@FeignClient(dismiss404 = true)` on `OperatonClient` already swallows the 404, so the behaviour would be the same either way. The flag still earns its place: it states the intent at the call rather than leaning on a client-wide setting that applies to every other request too, and it keeps Operaton from logging a failure of its own.
