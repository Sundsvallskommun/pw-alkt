package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.JsonParameter;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import generated.se.sundsvall.supportmanagement.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.employee.EmployeeIntegration;
import se.sundsvall.alkt.integration.jsonschema.JsonSchemaIntegration;
import se.sundsvall.alkt.integration.party.PartyIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionBasisServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String PREMISES_MUNICIPALITY_ID = "2262";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "c2b1f9a4-3d1e-4a57-9a0e-6f1e2b7d8c90";
	private static final String TEMPLATE = "serving-permit-decision-basis";
	private static final String FILE_NAME = "Beslutsunderlag MBN 2026-123.pdf";
	private static final String PBI_PARTY_ID = "8f3a2c1e-5b6d-4e7f-9a0b-1c2d3e4f5a6b";
	private static final String CASE_WORKER = "er12lin";
	private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.parse("2026-10-30T14:00:00+01:00");
	private static final byte[] PDF = {
		37, 80, 68, 70
	};

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private TemplatingIntegration templatingIntegrationMock;

	@Mock
	private PartyIntegration partyIntegrationMock;

	@Mock
	private JsonSchemaIntegration jsonSchemaIntegrationMock;

	@Mock
	private EmployeeIntegration employeeIntegrationMock;

	@Captor
	private ArgumentCaptor<Map<String, Object>> parametersCaptor;

	private DecisionBasisService service;

	@BeforeEach
	void setUp() {
		final var clock = Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneId.of("Europe/Stockholm"));
		service = new DecisionBasisService(supportManagementIntegrationMock, templatingIntegrationMock, partyIntegrationMock, jsonSchemaIntegrationMock, employeeIntegrationMock, clock);
	}

	@AfterEach
	void verifyNoUnexpectedInteractions() {
		verifyNoMoreInteractions(supportManagementIntegrationMock, templatingIntegrationMock, partyIntegrationMock, jsonSchemaIntegrationMock, employeeIntegrationMock);
	}

	private static Errand errand() {
		return new Errand().id(ERRAND_ID).errandNumber("ALKT-2026-000123").assignedUserId(CASE_WORKER)
			.parameters(List.of(new Parameter().key("premisesMunicipalityId").values(List.of(PREMISES_MUNICIPALITY_ID)),
				new Parameter().key("externalCaseRegistrationNumber").values(List.of("MBN 2026/123"))))
			.stakeholders(List.of(new Stakeholder().role("CONTACT").externalId(PBI_PARTY_ID).firstName("Anna").lastName("Svensson")
				.parameters(List.of(new Parameter().key("PBI").values(List.of("true"))))));
	}

	@Test
	void rendersTheDecisionBasisFromAnInvestigationThatIsADraftAndUploadsItToTheErrand() {
		final var form = JsonMapper.builder().build().readTree("{\"servingAudience\":\"PUBLIC\"}");
		final var schema = JsonMapper.builder().build().readTree("{\"properties\":{\"servingAudience\":{\"oneOf\":[{\"const\":\"PUBLIC\",\"title\":\"Allmänheten\"}]}}}");
		final var investigation = new Investigation().id("investigation-id").status("DRAFT").created(COMPLETED_AT);
		final var unanswered = new Statement().id("referral-id").type("REFERRAL").status("SENT").counterpartyName("Polismyndigheten").sentAt(COMPLETED_AT);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errand().jsonParameters(List.of(new JsonParameter().key("aot_alcohol_serving_permit_application_permanent_serving").schemaId("2281_aot_alcohol_serving_permit_application_permanent_serving_1.0").value(form))));
		when(jsonSchemaIntegrationMock.getSchema(MUNICIPALITY_ID, "2281_aot_alcohol_serving_permit_application_permanent_serving_1.0")).thenReturn(schema);
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(investigation));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getStatements(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(unanswered));
		when(partyIntegrationMock.findLegalIds(MUNICIPALITY_ID, List.of(PBI_PARTY_ID))).thenReturn(Map.of(PBI_PARTY_ID, "198203154321"));
		when(employeeIntegrationMock.findName(MUNICIPALITY_ID, CASE_WORKER)).thenReturn("Erik Lindqvist");
		when(supportManagementIntegrationMock.getDecisionOutcomeNames(MUNICIPALITY_ID, NAMESPACE)).thenReturn(Map.of("APPROVAL", "Bifall"));
		when(supportManagementIntegrationMock.getStatementOutcomeNames(MUNICIPALITY_ID, NAMESPACE)).thenReturn(Map.of());
		when(templatingIntegrationMock.renderPdf(eq(PREMISES_MUNICIPALITY_ID), eq(TEMPLATE), anyMap())).thenReturn(PDF);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF)).thenReturn("attachment-id");

		assertThat(service.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getStatements(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).findLegalIds(MUNICIPALITY_ID, List.of(PBI_PARTY_ID));
		verify(employeeIntegrationMock).findName(MUNICIPALITY_ID, CASE_WORKER);
		verify(jsonSchemaIntegrationMock).getSchema(MUNICIPALITY_ID, "2281_aot_alcohol_serving_permit_application_permanent_serving_1.0");
		verify(supportManagementIntegrationMock).getDecisionOutcomeNames(MUNICIPALITY_ID, NAMESPACE);
		verify(supportManagementIntegrationMock).getStatementOutcomeNames(MUNICIPALITY_ID, NAMESPACE);
		verify(templatingIntegrationMock).renderPdf(eq(PREMISES_MUNICIPALITY_ID), eq(TEMPLATE), parametersCaptor.capture());
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF);
		final var parameters = parametersCaptor.getValue();
		assertThat(parameters).containsEntry("documentDate", "2026-11-03").containsEntry("caseNumber", "MBN 2026/123")
			.containsEntry("handlerName", "Erik Lindqvist");
		assertThat(parameters.get("application")).asInstanceOf(MAP).containsEntry("servingTo", "Allmänheten");
		assertThat(parameters.get("personsOfInfluence")).asInstanceOf(LIST).singleElement()
			.asInstanceOf(MAP).containsEntry("name", "Anna Svensson").containsEntry("personalNumber", "19820315-4321");
		assertThat(parameters.get("statements")).asInstanceOf(LIST).singleElement()
			.asInstanceOf(MAP).containsEntry("counterparty", "Polismyndigheten").containsEntry("respondedAt", "");
	}

	@Test
	void createsANewDecisionBasisWhenTheFileOfThatNameWasUploadedBeforeTheInvestigationWasCompleted() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().id("investigation-id").status("COMPLETED").completedAt(COMPLETED_AT)));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("outdated-id").fileName(FILE_NAME).created(COMPLETED_AT.minusDays(1))));
		when(supportManagementIntegrationMock.getStatements(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(partyIntegrationMock.findLegalIds(MUNICIPALITY_ID, List.of(PBI_PARTY_ID))).thenReturn(Map.of());
		when(employeeIntegrationMock.findName(MUNICIPALITY_ID, CASE_WORKER)).thenReturn("Erik Lindqvist");
		when(supportManagementIntegrationMock.getDecisionOutcomeNames(MUNICIPALITY_ID, NAMESPACE)).thenReturn(Map.of("APPROVAL", "Bifall"));
		when(supportManagementIntegrationMock.getStatementOutcomeNames(MUNICIPALITY_ID, NAMESPACE)).thenReturn(Map.of());
		when(templatingIntegrationMock.renderPdf(eq(PREMISES_MUNICIPALITY_ID), eq(TEMPLATE), anyMap())).thenReturn(PDF);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF)).thenReturn("attachment-id");

		assertThat(service.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getStatements(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).findLegalIds(MUNICIPALITY_ID, List.of(PBI_PARTY_ID));
		verify(employeeIntegrationMock).findName(MUNICIPALITY_ID, CASE_WORKER);
		verify(supportManagementIntegrationMock).getDecisionOutcomeNames(MUNICIPALITY_ID, NAMESPACE);
		verify(supportManagementIntegrationMock).getStatementOutcomeNames(MUNICIPALITY_ID, NAMESPACE);
		verify(templatingIntegrationMock).renderPdf(eq(PREMISES_MUNICIPALITY_ID), eq(TEMPLATE), anyMap());
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF);
	}

	@Test
	void rendersTheDecisionBasisWithAnEmptyHandlerNameWhenTheRegisterDoesNotKnowTheCaseWorker() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().id("investigation-id").status("COMPLETED").completedAt(COMPLETED_AT)));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getStatements(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(partyIntegrationMock.findLegalIds(MUNICIPALITY_ID, List.of(PBI_PARTY_ID))).thenReturn(Map.of());
		when(employeeIntegrationMock.findName(MUNICIPALITY_ID, CASE_WORKER)).thenReturn("");
		when(supportManagementIntegrationMock.getDecisionOutcomeNames(MUNICIPALITY_ID, NAMESPACE)).thenReturn(Map.of());
		when(supportManagementIntegrationMock.getStatementOutcomeNames(MUNICIPALITY_ID, NAMESPACE)).thenReturn(Map.of());
		when(templatingIntegrationMock.renderPdf(eq(PREMISES_MUNICIPALITY_ID), eq(TEMPLATE), anyMap())).thenReturn(PDF);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF)).thenReturn("attachment-id");

		assertThat(service.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getStatements(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).findLegalIds(MUNICIPALITY_ID, List.of(PBI_PARTY_ID));
		verify(employeeIntegrationMock).findName(MUNICIPALITY_ID, CASE_WORKER);
		verify(supportManagementIntegrationMock).getDecisionOutcomeNames(MUNICIPALITY_ID, NAMESPACE);
		verify(supportManagementIntegrationMock).getStatementOutcomeNames(MUNICIPALITY_ID, NAMESPACE);
		verify(templatingIntegrationMock).renderPdf(eq(PREMISES_MUNICIPALITY_ID), eq(TEMPLATE), parametersCaptor.capture());
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF);
		assertThat(parametersCaptor.getValue()).containsEntry("handlerName", "");
	}

	@Test
	void failsWithoutRetryWhenTheErrandHasNoInvestigation() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());

		assertThatThrownBy(() -> service.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'c2b1f9a4-3d1e-4a57-9a0e-6f1e2b7d8c90' has no investigation that is not cancelled to create a decision basis from");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void leavesOutACancelledInvestigation() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().id("cancelled-id").status("CANCELLED")));

		assertThatThrownBy(() -> service.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'c2b1f9a4-3d1e-4a57-9a0e-6f1e2b7d8c90' has no investigation that is not cancelled to create a decision basis from");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void failsWithoutRetryWhenTheErrandHasTwoInvestigations() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().id("completed-id").status("COMPLETED"), new Investigation().id("draft-id").status("DRAFT")));

		assertThatThrownBy(() -> service.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'c2b1f9a4-3d1e-4a57-9a0e-6f1e2b7d8c90' has 2 investigations that are not cancelled, the decision basis is created from exactly one");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void failsWithoutRetryWhenTheErrandHasNoAssignedCaseWorker(final String assignedUserId) {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand().assignedUserId(assignedUserId));
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().id("investigation-id").status("COMPLETED").completedAt(COMPLETED_AT)));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());

		assertThatThrownBy(() -> service.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'c2b1f9a4-3d1e-4a57-9a0e-6f1e2b7d8c90' has no assigned case worker to put in the decision basis");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void keepsTheDecisionBasisUploadedEarlierEvenWhenTheErrandHasNoAssignedCaseWorker() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand().assignedUserId(null));
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().id("investigation-id").status("COMPLETED").completedAt(COMPLETED_AT)));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("existing-id").fileName(FILE_NAME).created(COMPLETED_AT.plusMinutes(1))));

		assertThat(service.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("existing-id");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}
}
