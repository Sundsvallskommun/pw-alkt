package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.JsonParameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.jsonschema.JsonSchemaIntegration;
import se.sundsvall.alkt.integration.party.PartyIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.alkt.integration.templating.model.Section;
import se.sundsvall.alkt.service.model.SubmissionPdfOutcome;

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toAttachmentSection;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toErrandSection;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toSections;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toStakeholderSection;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toTitle;

/** Puts a PDF of the submission the errand came in with on the errand, so it can be entered in the records. */
@Service
public class SubmissionPdfService {

	private static final String TEMPLATE_ID = "alkt.form-pdf";
	private static final String TEMPLATE_VERSION = "1.0";

	private final SupportManagementIntegration supportManagementIntegration;
	private final JsonSchemaIntegration jsonSchemaIntegration;
	private final TemplatingIntegration templatingIntegration;
	private final PartyIntegration partyIntegration;

	SubmissionPdfService(final SupportManagementIntegration supportManagementIntegration, final JsonSchemaIntegration jsonSchemaIntegration,
		final TemplatingIntegration templatingIntegration, final PartyIntegration partyIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.jsonSchemaIntegration = jsonSchemaIntegration;
		this.templatingIntegration = templatingIntegration;
		this.partyIntegration = partyIntegration;
	}

	/**
	 * The submission is the first JSON parameter of the errand; an errand without one came in some other way and has no
	 * submission to make a PDF of. A rerun finds the PDF an earlier attempt uploaded by its file name.
	 */
	public SubmissionPdfOutcome createSubmissionPdf(final String municipalityId, final String namespace, final String errandId) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var submission = Optional.ofNullable(errand.getJsonParameters()).orElse(emptyList()).stream().findFirst();
		if (submission.isEmpty()) {
			return SubmissionPdfOutcome.SKIPPED;
		}

		final var fileName = toTitle(errand) + ".pdf";
		final var attachments = supportManagementIntegration.getAttachments(municipalityId, namespace, errandId);
		if (attachments.stream().anyMatch(attachment -> fileName.equals(attachment.getFileName()))) {
			return SubmissionPdfOutcome.ALREADY_EXISTS;
		}

		final var pdf = templatingIntegration.renderPdf(municipalityId, TEMPLATE_ID, TEMPLATE_VERSION,
			toParameters(municipalityId, errand, submission.get(), attachments));
		supportManagementIntegration.createPdfAttachment(municipalityId, namespace, errandId, fileName, pdf);
		return SubmissionPdfOutcome.CREATED;
	}

	private Map<String, Object> toParameters(final String municipalityId, final Errand errand, final JsonParameter submission,
		final List<ErrandAttachment> attachments) {
		final var schema = jsonSchemaIntegration.getSchema(municipalityId, submission.getSchemaId());
		final var uiSchema = jsonSchemaIntegration.getUiSchema(municipalityId, submission.getSchemaId());
		final var stakeholders = Optional.ofNullable(errand.getStakeholders()).orElse(emptyList());

		final var sections = new ArrayList<Section>();
		sections.add(toErrandSection(errand));
		sections.addAll(toSections(schema, uiSchema, submission.getValue()));
		toStakeholderSection(stakeholders, legalIdsOf(municipalityId, stakeholders)).ifPresent(sections::add);
		toAttachmentSection(attachments, schema).ifPresent(sections::add);
		return Map.of("title", toTitle(errand), "sections", sections);
	}

	// Why: a party the register does not know is shown without a number rather than holding the errand.
	private Map<String, String> legalIdsOf(final String municipalityId, final List<Stakeholder> stakeholders) {
		final var legalIds = new HashMap<String, String>();
		stakeholders.stream()
			.map(Stakeholder::getExternalId)
			.filter(Objects::nonNull)
			.distinct()
			.forEach(partyId -> partyIntegration.findLegalId(municipalityId, partyId).ifPresent(legalId -> legalIds.put(partyId, legalId)));
		return legalIds;
	}
}
