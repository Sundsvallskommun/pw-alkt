package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Investigation;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.dept44.problem.Problem;

import static org.apache.commons.lang3.StringUtils.isAnyBlank;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.Constants.INVESTIGATION_STATUS_COMPLETED;
import static se.sundsvall.alkt.integration.templating.mapper.TemplatingMapper.toTemplateParameters;

@Service
public class InspectionProtocolService {

	private final SupportManagementIntegration supportManagementIntegration;
	private final TemplatingIntegration templatingIntegration;

	InspectionProtocolService(final SupportManagementIntegration supportManagementIntegration, final TemplatingIntegration templatingIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.templatingIntegration = templatingIntegration;
	}

	/**
	 * A rerun finds the protocol an earlier attempt uploaded by its file name and by it being uploaded after the
	 * investigation was completed, and uploads no second one.
	 */
	public String createProtocol(final String municipalityId, final String namespace, final String errandId, final String template, final String fileName) {
		if (isAnyBlank(template, fileName)) {
			throw new NonRetryableException("The step has no protocol template or file name, see the input parameters in the bpmn schema");
		}

		final var investigation = getInvestigation(municipalityId, namespace, errandId);
		final var completed = completedAt(investigation);
		return supportManagementIntegration.getAttachments(municipalityId, namespace, errandId).stream()
			.filter(attachment -> fileName.equals(attachment.getFileName()))
			.filter(attachment -> Optional.ofNullable(attachment.getCreated()).filter(created -> !created.isBefore(completed)).isPresent())
			.map(ErrandAttachment::getId)
			.findFirst()
			.orElseGet(() -> uploadProtocol(municipalityId, namespace, errandId, template, fileName, investigation));
	}

	private String uploadProtocol(final String municipalityId, final String namespace, final String errandId, final String template, final String fileName,
		final Investigation investigation) {
		final var pdf = templatingIntegration.renderPdf(municipalityId, template, toTemplateParameters(investigation));
		return supportManagementIntegration.createPdfAttachment(municipalityId, namespace, errandId, fileName, pdf);
	}

	// Why: a file of that name uploaded before the completion, by a case worker or a cancelled earlier process, is not this
	// protocol. completedAt is set by the client and may be missing; modified moves with every later change.
	private static OffsetDateTime completedAt(final Investigation investigation) {
		return Optional.ofNullable(investigation.getCompletedAt())
			.or(() -> Optional.ofNullable(investigation.getModified()))
			.orElse(investigation.getCreated());
	}

	// Why: the step runs once the case worker has completed the investigation, so that is the one the protocol is of. A
	// draft, active or cancelled one is not, and with several completed there is no telling which one it is. None is
	// retried, since the completion may not be saved yet when the phase ends.
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
