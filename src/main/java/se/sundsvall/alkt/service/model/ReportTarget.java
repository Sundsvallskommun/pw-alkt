package se.sundsvall.alkt.service.model;

/**
 * Which process row in Support Management a report is about. A work step has all of it on its external task; the
 * reconciliation of instances that are no longer running reads it from the history of the engine.
 *
 * @param municipalityId    municipality the errand belongs to
 * @param namespace         namespace the errand belongs to
 * @param errandId          the errand the process drives
 * @param processInstanceId the instance in the engine
 * @param processKey        the process model the instance runs, as the engine names it
 * @param externalTaskId    the external task the report is made from. Optional; it is what makes a replayed report
 *                          add no duplicate activities
 */
public record ReportTarget(
	String municipalityId,
	String namespace,
	String errandId,
	String processInstanceId,
	String processKey,
	String externalTaskId) {}
