package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.employee.EmployeeIntegration;
import se.sundsvall.alkt.integration.jsonschema.JsonSchemaIntegration;
import se.sundsvall.alkt.integration.party.PartyIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.alkt.integration.templating.mapper.DecisionBasisMapper.Lookups;
import tools.jackson.databind.node.MissingNode;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findAttachmentId;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findFormParameter;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseFileName;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCompletedAt;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPremisesMunicipalityId;
import static se.sundsvall.alkt.integration.templating.mapper.DecisionBasisMapper.toTemplateParameters;

@Service
public class DecisionBasisService {

	private static final String INVESTIGATION_STATUS_CANCELLED = "CANCELLED";

	private final SupportManagementIntegration supportManagementIntegration;
	private final TemplatingIntegration templatingIntegration;
	private final PartyIntegration partyIntegration;
	private final JsonSchemaIntegration jsonSchemaIntegration;
	private final EmployeeIntegration employeeIntegration;
	private final Clock clock;

	DecisionBasisService(final SupportManagementIntegration supportManagementIntegration, final TemplatingIntegration templatingIntegration,
		final PartyIntegration partyIntegration, final JsonSchemaIntegration jsonSchemaIntegration, final EmployeeIntegration employeeIntegration, final Clock clock) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.templatingIntegration = templatingIntegration;
		this.partyIntegration = partyIntegration;
		this.jsonSchemaIntegration = jsonSchemaIntegration;
		this.employeeIntegration = employeeIntegration;
		this.clock = clock;
	}

	/**
	 * A rerun finds the decision basis an earlier attempt uploaded by its file name and by it being uploaded after the
	 * investigation was completed, or last changed when it has no completion time, and uploads no second one.
	 */
	public String createDecisionBasis(final String municipalityId, final String namespace, final String errandId, final String template) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var investigations = supportManagementIntegration.getInvestigations(municipalityId, namespace, errandId).stream()
			.filter(investigation -> !INVESTIGATION_STATUS_CANCELLED.equals(investigation.getStatus()))
			.toList();
		if (investigations.isEmpty()) {
			throw new NonRetryableException("Errand '%s' has no investigation that is not cancelled to create a decision basis from".formatted(errandId));
		}
		if (investigations.size() > 1) {
			throw new NonRetryableException("Errand '%s' has %d investigations that are not cancelled, the decision basis is created from exactly one".formatted(errandId, investigations.size()));
		}
		final var investigation = investigations.getFirst();
		final var fileName = toCaseFileName("Beslutsunderlag", errand);
		final var completed = toCompletedAt(investigation);
		final var attachments = supportManagementIntegration.getAttachments(municipalityId, namespace, errandId);
		return findAttachmentId(attachments, fileName, completed)
			.orElseGet(() -> {
				if (isBlank(errand.getAssignedUserId())) {
					throw new NonRetryableException("Errand '%s' has no assigned case worker to put in the decision basis".formatted(errandId));
				}
				final var statements = supportManagementIntegration.getStatements(municipalityId, namespace, errandId);
				final var parameters = toTemplateParameters(errand, investigation, statements, attachments, lookupsOf(municipalityId, namespace, errand), LocalDate.now(clock));
				final var pdf = templatingIntegration.renderPdf(toPremisesMunicipalityId(errand), template, parameters);
				return supportManagementIntegration.createPdfAttachment(municipalityId, namespace, errandId, fileName, pdf);
			});
	}

	private Lookups lookupsOf(final String municipalityId, final String namespace, final Errand errand) {
		final var formSchema = findFormParameter(errand)
			.map(form -> jsonSchemaIntegration.getSchema(municipalityId, form.getSchemaId()))
			.orElse(MissingNode.getInstance());
		return new Lookups(formSchema, supportManagementIntegration.getDecisionOutcomeNames(municipalityId, namespace),
			supportManagementIntegration.getStatementOutcomeNames(municipalityId, namespace), legalIdsOfPersonsOfInfluence(municipalityId, errand),
			employeeIntegration.getName(municipalityId, errand.getAssignedUserId()));
	}

	private Map<String, String> legalIdsOfPersonsOfInfluence(final String municipalityId, final Errand errand) {
		return partyIntegration.findLegalIds(municipalityId, Optional.ofNullable(errand.getStakeholders()).orElse(emptyList()).stream()
			.filter(SupportManagementMapper::isPersonOfSignificantInfluence)
			.map(Stakeholder::getExternalId)
			.toList());
	}
}
