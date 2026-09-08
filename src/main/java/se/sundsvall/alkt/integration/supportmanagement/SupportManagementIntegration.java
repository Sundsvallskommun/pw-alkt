package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Errand;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.api.model.ProcessStateReport;

@Component
public class SupportManagementIntegration {

	private final SupportManagementClient supportManagementClient;

	SupportManagementIntegration(final SupportManagementClient supportManagementClient) {
		this.supportManagementClient = supportManagementClient;
	}

	public void patchProcessState(final String municipalityId, final String namespace, final String errandId, final ProcessStateReport report) {
		supportManagementClient.patchErrand(municipalityId, namespace, errandId, toIfMatch(report.errandVersion()), false, toPatchBody());
	}

	// null skips the version check (the engine's own doc for If-Match: "omit to skip version check"); a report with
	// no errandVersion is a status-only, unconditional write.
	private static String toIfMatch(final Long errandVersion) {
		if (errandVersion == null) {
			return null;
		}

		return "\"%s\"".formatted(errandVersion);
	}

	// Patches nothing yet - which field on the errand should carry process status is still open with Support
	// Management (see DRAKEN-4745). This exercises the real PATCH, with its If-Match/412 handling, without touching
	// any data on the errand. Errand's collections default to empty-but-present lists, which would otherwise
	// serialize and risk being read by Support Management as "clear this collection".
	private static Errand toPatchBody() {
		return new Errand()
			.stakeholders(null)
			.externalTags(null)
			.parameters(null)
			.jsonParameters(null)
			.labels(null)
			.phases(null)
			.activeNotifications(null)
			.actions(null)
			.measures(null);
	}
}
