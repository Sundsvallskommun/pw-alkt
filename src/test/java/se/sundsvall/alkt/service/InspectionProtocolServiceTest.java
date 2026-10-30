package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionProtocolServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String TEMPLATE = "internal-inspection-protocol";
	private static final String ERRAND_NUMBER = "AOT-26100148";
	private static final String FILE_NAME = "Tillsynsprotokoll AOT-26100148.pdf";
	private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-09-15T09:00:00+02:00");
	private static final OffsetDateTime COMPLETED = OffsetDateTime.parse("2026-09-20T10:00:00+02:00");
	private static final byte[] PDF = {
		1, 2, 3
	};
	private static final Measure CHECKPOINT = new Measure().type("INTERNAL").status("ACTIVE").title("Kassaregister").result("COMPLETED").created(CREATED);
	private static final List<Map<String, String>> CHECKPOINT_ROWS = List.of(Map.of("title", "Kassaregister", "noRemark", "X", "remark", "", "comment", ""));

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private TemplatingIntegration templatingIntegrationMock;

	@InjectMocks
	private InspectionProtocolService service;

	@AfterEach
	void verifyNoUnexpectedInteractions() {
		verifyNoMoreInteractions(supportManagementIntegrationMock, templatingIntegrationMock);
	}

	private static Errand sundsvallErrand() {
		return new Errand().id(ERRAND_ID).errandNumber(ERRAND_NUMBER).measures(new ArrayList<>(List.of(CHECKPOINT)));
	}

	private static Errand timraErrand(final List<String> externalReferenceNumbers) {
		final var parameters = new ArrayList<Parameter>();
		parameters.add(new Parameter().key("premisesMunicipalityId").values(List.of("2262")));
		if (externalReferenceNumbers != null) {
			parameters.add(new Parameter().key("externalReferenceNumber").values(externalReferenceNumbers));
		}
		return new Errand().id(ERRAND_ID).errandNumber(ERRAND_NUMBER).parameters(parameters).measures(new ArrayList<>(List.of(CHECKPOINT)));
	}

	private static Investigation completedInvestigation() {
		return new Investigation().status("COMPLETED").created(CREATED).completedAt(COMPLETED).modified(COMPLETED);
	}

	@Test
	void createsTheProtocolFromTheInvestigation() {
		final var investigation = completedInvestigation().type("serving_permit").parameters(List.of(new Parameter().key("businessName").values(List.of("Runt Hörnet AB"))));
		final var cancelled = new Investigation().status("CANCELLED").created(CREATED).parameters(List.of(new Parameter().key("businessName").values(List.of("Gamla Namnet AB"))));
		final var expectedParameters = Map.of("businessName", "Runt Hörnet AB", "measures", CHECKPOINT_ROWS, "caseNumber", ERRAND_NUMBER);
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(cancelled, investigation));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(sundsvallErrand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new ErrandAttachment().id("other-id").fileName("Foto.jpg").created(COMPLETED.plusSeconds(1)),
			new ErrandAttachment().id("scanned-id").fileName(FILE_NAME).created(COMPLETED.minusSeconds(1))));
		when(templatingIntegrationMock.renderPdf(MUNICIPALITY_ID, TEMPLATE, expectedParameters)).thenReturn(PDF);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF)).thenReturn("attachment-id");

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(templatingIntegrationMock).renderPdf(MUNICIPALITY_ID, TEMPLATE, expectedParameters);
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF);
	}

	@Test
	void usesTheErrandNumberAsCaseNumberInSundsvall() {
		final var errand = sundsvallErrand().errandNumber("AOT-26100147")
			.parameters(List.of(new Parameter().key("premisesMunicipalityId").values(List.of("2281")), new Parameter().key("externalReferenceNumber").values(List.of("KS 2026/00999"))));
		final var expectedParameters = Map.of("measures", CHECKPOINT_ROWS, "caseNumber", "AOT-26100147");
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(completedInvestigation()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(templatingIntegrationMock.renderPdf(MUNICIPALITY_ID, TEMPLATE, expectedParameters)).thenReturn(PDF);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "Tillsynsprotokoll AOT-26100147.pdf", PDF)).thenReturn("attachment-id");

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(templatingIntegrationMock).renderPdf(MUNICIPALITY_ID, TEMPLATE, expectedParameters);
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "Tillsynsprotokoll AOT-26100147.pdf", PDF);
	}

	@ParameterizedTest
	@MethodSource("missingCaseNumbersOfTimra")
	void failsWithoutRetryWhenTheErrandInTimraHasNoCaseNumber(final List<String> externalReferenceNumbers) {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(completedInvestigation()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(timraErrand(externalReferenceNumbers));

		assertThatThrownBy(() -> service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' in Timrå has no case number in externalReferenceNumber, so no protocol is made");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	private static Stream<List<String>> missingCaseNumbersOfTimra() {
		return Stream.of(null, List.of(), List.of(" ", ""));
	}

	@ParameterizedTest
	@ValueSource(longs = {
		0, 1
	})
	void answersWithTheProtocolAnEarlierAttemptUploadedAlthoughTheInvestigationChangedSince(final long secondsAfterCompleted) {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().status("COMPLETED").created(CREATED).completedAt(COMPLETED).modified(COMPLETED.plusDays(2))));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(sundsvallErrand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("attachment-id").fileName(FILE_NAME).created(COMPLETED.plusSeconds(secondsAfterCompleted))));

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	@ParameterizedTest
	@MethodSource("investigationsWithoutCompletedAt")
	void comparesWithTheLastChangeOfAnInvestigationWithoutCompletedAt(final Investigation investigation) {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(investigation));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(sundsvallErrand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new ErrandAttachment().id("scanned-id").fileName(FILE_NAME).created(COMPLETED.minusSeconds(1)),
			new ErrandAttachment().id("attachment-id").fileName(FILE_NAME).created(COMPLETED)));

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	private static Stream<Investigation> investigationsWithoutCompletedAt() {
		return Stream.of(
			new Investigation().status("COMPLETED").created(CREATED).modified(COMPLETED),
			new Investigation().status("COMPLETED").created(COMPLETED));
	}

	@Test
	void uploadsAgainWhenTheAttachmentOfThatNameHasNoCreation() {
		final var expectedParameters = Map.of("measures", CHECKPOINT_ROWS, "caseNumber", ERRAND_NUMBER);
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(completedInvestigation()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(sundsvallErrand());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new ErrandAttachment().id("unknown-id").fileName(FILE_NAME)));
		when(templatingIntegrationMock.renderPdf(MUNICIPALITY_ID, TEMPLATE, expectedParameters)).thenReturn(PDF);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF)).thenReturn("attachment-id");

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(templatingIntegrationMock).renderPdf(MUNICIPALITY_ID, TEMPLATE, expectedParameters);
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, PDF);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"DRAFT", "ACTIVE", "CANCELLED"
	})
	void failsWithRetryWithoutACompletedInvestigation(final String status) {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Investigation().status(status)));

		assertThatThrownBy(() -> service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("Errand 'errand-id' has no completed investigation to create a protocol from");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void failsWithoutRetryWithSeveralInvestigations() {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Investigation().status("COMPLETED"), new Investigation().status("COMPLETED")));

		assertThatThrownBy(() -> service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has 2 completed investigations, the protocol is created from exactly one");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"", " "
	})
	void failsWithoutRetryWhenTheStepHasNoTemplate(final String template) {
		assertThatThrownBy(() -> service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, template))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The step has no protocol template, see the input parameters in the bpmn schema");

		verifyNoInteractions(supportManagementIntegrationMock, templatingIntegrationMock);
	}

	@Test
	void rendersTheServingProtocolWithItsOrderInTimra() {
		final var errand = timraErrand(List.of("KS 2026/00123"));
		errand.addMeasuresItem(new Measure().type("OBSERVATION").status("ACTIVE").title("Uteservering").result("NOT_COMPLETED").resultText("Smoking").created(CREATED));
		final var expectedParameters = Map.of("measures", CHECKPOINT_ROWS, "observationSmokingOutdoor", "X", "orderWithRemarks", "true", "caseNumber", "KS 2026/00123");
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(completedInvestigation()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(templatingIntegrationMock.renderPdf("2262", "serving-inspection-protocol", expectedParameters)).thenReturn(PDF);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "Tillsynsprotokoll KS 2026-00123.pdf", PDF)).thenReturn("attachment-id");

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "serving-inspection-protocol")).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(templatingIntegrationMock).renderPdf("2262", "serving-inspection-protocol", expectedParameters);
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "Tillsynsprotokoll KS 2026-00123.pdf", PDF);
	}

	@ParameterizedTest
	@MethodSource("measuresWithoutActiveCheckpoints")
	void failsWithoutRetryWithoutActiveCheckpoints(final List<Measure> measures) {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(completedInvestigation()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(sundsvallErrand().measures(measures));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());

		assertThatThrownBy(() -> service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no active checkpoints, so no protocol is made");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	private static Stream<List<Measure>> measuresWithoutActiveCheckpoints() {
		return Stream.of(
			null,
			List.of(),
			List.of(new Measure().type("INTERNAL").status("CANCELLED").title("Kassaregister").result("COMPLETED").created(CREATED),
				new Measure().type("EXTERNAL").status("ACTIVE").title("Åtgärda policyn").created(CREATED),
				new Measure().type("OBSERVATION").status("ACTIVE").title("Uteservering").result("COMPLETED").created(CREATED)));
	}

}
