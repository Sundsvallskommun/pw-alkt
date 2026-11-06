package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Investigation;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findAttachmentId;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseFileName;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseNumber;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCompletedAt;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPremisesMunicipalityId;
import static se.sundsvall.alkt.integration.templating.mapper.InspectionProtocolMapper.toTemplateParameters;

@Service
public class InspectionProtocolService {

	private static final String INVESTIGATION_STATUS_COMPLETED = "COMPLETED";
	private static final Map<String, Protocol> PROTOCOLS_BY_INVESTIGATION_TYPE = Map.of(
		"serving_permit", new Protocol("serving-inspection-protocol", "aot_inspection_serving_permit"),
		"internal_inspection", new Protocol("internal-inspection-protocol", "aot_inspection_internal"));

	/** The template of a protocol, and the key of the JSON parameter of the investigation that holds its answers. */
	private record Protocol(String template, String answersKey) {}

	private final SupportManagementIntegration supportManagementIntegration;
	private final TemplatingIntegration templatingIntegration;

	InspectionProtocolService(final SupportManagementIntegration supportManagementIntegration, final TemplatingIntegration templatingIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.templatingIntegration = templatingIntegration;
	}

	/**
	 * Renders the protocol from the answers in the JSON parameter of the investigation and attaches it to the errand. A
	 * rerun finds the protocol an earlier attempt uploaded by its file name and by it being uploaded after the
	 * investigation was completed, and uploads no second one.
	 */
	public String createProtocol(final String municipalityId, final String namespace, final String errandId) {
		final var investigation = getInvestigation(municipalityId, namespace, errandId);
		final var protocol = Optional.ofNullable(investigation.getType()).map(PROTOCOLS_BY_INVESTIGATION_TYPE::get)
			.orElseThrow(() -> new NonRetryableException("Errand '%s' has an investigation of type '%s', which is none of the inspection protocols %s".formatted(errandId,
				investigation.getType(), PROTOCOLS_BY_INVESTIGATION_TYPE.keySet())));
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var caseNumber = toCaseNumber(errand);
		final var fileName = toCaseFileName("Tillsynsprotokoll", errand);
		final var completed = toCompletedAt(investigation);
		return findAttachmentId(supportManagementIntegration.getAttachments(municipalityId, namespace, errandId), fileName, completed)
			.orElseGet(() -> {
				final var answers = supportManagementIntegration.getInvestigationJson(municipalityId, namespace, errandId, investigation.getId(), protocol.answersKey());
				final var parameters = toTemplateParameters(errandId, protocol.template(), answers, errand.getMeasures(), caseNumber);
				final var pdf = templatingIntegration.renderPdf(toPremisesMunicipalityId(errand), protocol.template(), parameters);
				return supportManagementIntegration.createPdfAttachment(municipalityId, namespace, errandId, fileName, pdf);
			});
	}

	private Investigation getInvestigation(final String municipalityId, final String namespace, final String errandId) {
		final var investigations = supportManagementIntegration.getInvestigations(municipalityId, namespace, errandId).stream()
			.filter(investigation -> INVESTIGATION_STATUS_COMPLETED.equals(investigation.getStatus()))
			.toList();
		if (investigations.isEmpty()) {
			throw Problem.valueOf(NOT_FOUND, "Errand '%s' has no completed investigation to create a protocol from".formatted(errandId));
		}
		if (investigations.size() > 1) {
			throw new NonRetryableException("Errand '%s' has %d completed investigations, the protocol is created from exactly one".formatted(errandId, investigations.size()));
		}
		return investigations.getFirst();
	}
}
