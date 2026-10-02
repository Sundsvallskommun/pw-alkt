package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Investigation;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.dept44.problem.Problem;

import static org.apache.commons.lang3.StringUtils.isAnyBlank;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.integration.templating.mapper.TemplatingMapper.toTemplateParameters;

@Service
public class InspectionProtocolService {

	private final SupportManagementIntegration supportManagementIntegration;
	private final TemplatingIntegration templatingIntegration;

	InspectionProtocolService(final SupportManagementIntegration supportManagementIntegration, final TemplatingIntegration templatingIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.templatingIntegration = templatingIntegration;
	}

	/** A rerun finds the protocol an earlier attempt uploaded by its file name, and uploads no second one. */
	public String createProtocol(final String municipalityId, final String namespace, final String errandId, final String template, final String fileName) {
		if (isAnyBlank(template, fileName)) {
			throw new NonRetryableException("The step has no protocol template or file name, see the input parameters in the bpmn schema");
		}

		return supportManagementIntegration.getAttachments(municipalityId, namespace, errandId).stream()
			.filter(attachment -> fileName.equals(attachment.getFileName()))
			.map(ErrandAttachment::getId)
			.findFirst()
			.orElseGet(() -> uploadProtocol(municipalityId, namespace, errandId, template, fileName));
	}

	private String uploadProtocol(final String municipalityId, final String namespace, final String errandId, final String template, final String fileName) {
		final var pdf = templatingIntegration.renderPdf(municipalityId, template, toTemplateParameters(getInvestigation(municipalityId, namespace, errandId)));
		return supportManagementIntegration.createPdfAttachment(municipalityId, namespace, errandId, fileName, pdf);
	}

	// Why: an inspection has one investigation; with several there is no telling which one the protocol is of.
	private Investigation getInvestigation(final String municipalityId, final String namespace, final String errandId) {
		final var investigations = supportManagementIntegration.getInvestigations(municipalityId, namespace, errandId);
		if (investigations.isEmpty()) {
			throw Problem.valueOf(NOT_FOUND, "Errand '%s' has no investigation to create a protocol from".formatted(errandId));
		}
		if (investigations.size() > 1) {
			throw new NonRetryableException("Errand '%s' has %d investigations, the protocol is created from exactly one".formatted(errandId, investigations.size()));
		}
		return investigations.getFirst();
	}
}
