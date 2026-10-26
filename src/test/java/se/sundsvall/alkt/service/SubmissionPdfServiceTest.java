package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.JsonParameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.jsonschema.JsonSchemaIntegration;
import se.sundsvall.alkt.integration.party.PartyIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.alkt.integration.templating.model.GroupEntry;
import se.sundsvall.alkt.integration.templating.model.GroupField;
import se.sundsvall.alkt.integration.templating.model.Section;
import se.sundsvall.alkt.integration.templating.model.TableField;
import se.sundsvall.alkt.integration.templating.model.TableField.Row;
import se.sundsvall.alkt.integration.templating.model.ValueField;
import se.sundsvall.alkt.service.model.SubmissionPdfOutcome;
import se.sundsvall.dept44.exception.ClientProblem;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class SubmissionPdfServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-1";
	private static final String ERRAND_NUMBER = "ALKT-2026-000123";
	private static final String FILE_NAME = "Inkommen anmälan ALKT-2026-000123.pdf";
	private static final String SCHEMA_ID = "2281_folkol_0.11";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final JsonNode SCHEMA = JSON.readTree("""
		{"properties": {"namn": {"type": "string", "title": "Namn på stället"}},
		 "x-attachments": [{"key": "POWER_OF_ATTORNEY", "label": "Fullmakt"}]}
		""");
	private static final JsonNode UI_SCHEMA = JSON.readTree("""
		{"ui:sections": [{"title": "Försäljning", "fields": ["namn"]}]}
		""");

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private JsonSchemaIntegration jsonSchemaIntegrationMock;

	@Mock
	private TemplatingIntegration templatingIntegrationMock;

	@Mock
	private PartyIntegration partyIntegrationMock;

	@InjectMocks
	private SubmissionPdfService submissionPdfService;

	@AfterEach
	void verifyNoUnexpectedInteractions() {
		verifyNoMoreInteractions(supportManagementIntegrationMock, jsonSchemaIntegrationMock, templatingIntegrationMock, partyIntegrationMock);
	}

	private static Errand errand() {
		return new Errand()
			.id(ERRAND_ID)
			.errandNumber(ERRAND_NUMBER)
			.created(OffsetDateTime.parse("2026-10-21T08:00:00Z"))
			.jsonParameters(List.of(
				new JsonParameter().key("form").schemaId(SCHEMA_ID).value(JSON.readTree("{\"namn\": \"Kiosken\"}")),
				new JsonParameter().key("other").schemaId("2281_other_1.0").value(JSON.readTree("{\"annat\": \"svar\"}"))))
			.stakeholders(List.of(new Stakeholder().role("PRIMARY").externalId("party-1").externalIdType("COMPANY").organizationName("Kiosken AB")));
	}

	@Test
	void createsThePdfFromTheFirstJsonParameterAndUploadsItToTheErrand() {
		final var pdf = "%PDF".getBytes();
		final var attachment = new ErrandAttachment().fileName("fullmakt.pdf").purpose(new ErrandAttachmentPurpose().name("POWER_OF_ATTORNEY"));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(attachment));
		when(jsonSchemaIntegrationMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(SCHEMA);
		when(jsonSchemaIntegrationMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(UI_SCHEMA);
		when(partyIntegrationMock.findLegalId(MUNICIPALITY_ID, "party-1")).thenReturn(Optional.of("5566124144"));
		final Map<String, Object> expectedParameters = Map.of(
			"title", "Inkommen anmälan ALKT-2026-000123",
			"sections", List.of(
				new Section("Ärende", List.of(new ValueField("Ärendenummer", ERRAND_NUMBER), new ValueField("Inkomstdatum", "2026-10-21"))),
				new Section("Försäljning", List.of(new ValueField("Namn på stället", "Kiosken"))),
				new Section("Intressenter", List.of(new GroupField("PRIMARY", List.of(
					new GroupEntry("Namn", "Kiosken AB"), new GroupEntry("Organisationsnummer", "5566124144"))))),
				new Section("Bilagor", List.of(new TableField("Bilagor", List.of("Filnamn", "Typ"), List.of(Row.of("fullmakt.pdf", "Fullmakt")))))));
		when(templatingIntegrationMock.renderPdf(MUNICIPALITY_ID, "alkt.form-pdf", "1.0", expectedParameters)).thenReturn(pdf);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, pdf)).thenReturn("attachment-1");

		assertThat(submissionPdfService.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(SubmissionPdfOutcome.CREATED);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(jsonSchemaIntegrationMock).getSchema(MUNICIPALITY_ID, SCHEMA_ID);
		verify(jsonSchemaIntegrationMock).getUiSchema(MUNICIPALITY_ID, SCHEMA_ID);
		verify(partyIntegrationMock).findLegalId(MUNICIPALITY_ID, "party-1");
		verify(templatingIntegrationMock).renderPdf(MUNICIPALITY_ID, "alkt.form-pdf", "1.0", expectedParameters);
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, pdf);
	}

	@Test
	void anErrandWithoutJsonParametersIsSkippedWithoutFetchingRenderingOrUploading() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand().jsonParameters(List.of()));

		assertThat(submissionPdfService.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(SubmissionPdfOutcome.SKIPPED);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(jsonSchemaIntegrationMock, templatingIntegrationMock, partyIntegrationMock);
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void anErrandWithNullJsonParametersIsSkipped() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand().jsonParameters(null));

		assertThat(submissionPdfService.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(SubmissionPdfOutcome.SKIPPED);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(jsonSchemaIntegrationMock, templatingIntegrationMock, partyIntegrationMock);
	}

	@Test
	void aPdfAlreadyOnTheErrandIsNotRenderedOrUploadedAgain() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new ErrandAttachment().fileName(FILE_NAME)));

		assertThat(submissionPdfService.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(SubmissionPdfOutcome.ALREADY_EXISTS);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock, jsonSchemaIntegrationMock, partyIntegrationMock);
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void aStakeholderWhosePartyIsNotKnownIsShownWithoutNumber() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(jsonSchemaIntegrationMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(SCHEMA);
		when(jsonSchemaIntegrationMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(UI_SCHEMA);
		when(partyIntegrationMock.findLegalId(MUNICIPALITY_ID, "party-1")).thenReturn(Optional.empty());
		final Map<String, Object> expectedParameters = Map.of(
			"title", "Inkommen anmälan ALKT-2026-000123",
			"sections", List.of(
				new Section("Ärende", List.of(new ValueField("Ärendenummer", ERRAND_NUMBER), new ValueField("Inkomstdatum", "2026-10-21"))),
				new Section("Försäljning", List.of(new ValueField("Namn på stället", "Kiosken"))),
				new Section("Intressenter", List.of(new GroupField("PRIMARY", List.of(new GroupEntry("Namn", "Kiosken AB")))))));
		when(templatingIntegrationMock.renderPdf(MUNICIPALITY_ID, "alkt.form-pdf", "1.0", expectedParameters)).thenReturn("%PDF".getBytes());
		when(supportManagementIntegrationMock.createPdfAttachment(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(FILE_NAME), any())).thenReturn("attachment-1");

		assertThat(submissionPdfService.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(SubmissionPdfOutcome.CREATED);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(jsonSchemaIntegrationMock).getSchema(MUNICIPALITY_ID, SCHEMA_ID);
		verify(jsonSchemaIntegrationMock).getUiSchema(MUNICIPALITY_ID, SCHEMA_ID);
		verify(partyIntegrationMock).findLegalId(MUNICIPALITY_ID, "party-1");
		verify(templatingIntegrationMock).renderPdf(MUNICIPALITY_ID, "alkt.form-pdf", "1.0", expectedParameters);
		verify(supportManagementIntegrationMock).createPdfAttachment(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(FILE_NAME), any());
	}

	@Test
	void aFailingTemplatingIsLetThroughToBeRetried() {
		final var problem = new ClientProblem(BAD_GATEWAY, "templating is down");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(jsonSchemaIntegrationMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(SCHEMA);
		when(jsonSchemaIntegrationMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(UI_SCHEMA);
		when(partyIntegrationMock.findLegalId(MUNICIPALITY_ID, "party-1")).thenReturn(Optional.empty());
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq("alkt.form-pdf"), eq("1.0"), anyMap())).thenThrow(problem);

		assertThatThrownBy(() -> submissionPdfService.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isSameAs(problem);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(jsonSchemaIntegrationMock).getSchema(MUNICIPALITY_ID, SCHEMA_ID);
		verify(jsonSchemaIntegrationMock).getUiSchema(MUNICIPALITY_ID, SCHEMA_ID);
		verify(partyIntegrationMock).findLegalId(MUNICIPALITY_ID, "party-1");
		verify(templatingIntegrationMock).renderPdf(eq(MUNICIPALITY_ID), eq("alkt.form-pdf"), eq("1.0"), anyMap());
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void aFailingSupportManagementIsLetThroughToBeRetried() {
		final var problem = new ClientProblem(BAD_GATEWAY, "Support management is down");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(problem);

		assertThatThrownBy(() -> submissionPdfService.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isSameAs(problem);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void aTemplateThatCannotBeRenderedFailsWithoutRetry() {
		final var refused = new NonRetryableException("Template 'alkt.form-pdf' cannot be rendered from the given parameters: ClientProblem 400");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(jsonSchemaIntegrationMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(SCHEMA);
		when(jsonSchemaIntegrationMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(UI_SCHEMA);
		when(partyIntegrationMock.findLegalId(MUNICIPALITY_ID, "party-1")).thenReturn(Optional.empty());
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq("alkt.form-pdf"), eq("1.0"), anyMap())).thenThrow(refused);

		assertThatThrownBy(() -> submissionPdfService.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isSameAs(refused);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(jsonSchemaIntegrationMock).getSchema(MUNICIPALITY_ID, SCHEMA_ID);
		verify(jsonSchemaIntegrationMock).getUiSchema(MUNICIPALITY_ID, SCHEMA_ID);
		verify(partyIntegrationMock).findLegalId(MUNICIPALITY_ID, "party-1");
		verify(templatingIntegrationMock).renderPdf(eq(MUNICIPALITY_ID), eq("alkt.form-pdf"), eq("1.0"), anyMap());
	}
}
