package se.sundsvall.alkt.service.model;

/**
 * Which process row in Support Management a report is about. externalTaskId is optional, it dedups replayed activities.
 */
public record ReportTarget(
	String municipalityId,
	String namespace,
	String errandId,
	String processInstanceId,
	String processKey,
	String externalTaskId) {}
