package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;

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
	private static final String TEMPLATE = "inspection.external.protocol";
	private static final String FILE_NAME = "Tillsynsprotokoll.pdf";
	private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-09-15T09:00:00+02:00");
	private static final OffsetDateTime COMPLETED = OffsetDateTime.parse("2026-09-20T10:00:00+02:00");

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

	@Test
	void createsTheProtocolFromTheInvestigation() {
		final var pdf = new byte[] {
			1, 2, 3
		};
		final var investigation = new Investigation().status("COMPLETED").created(CREATED).modified(COMPLETED)
			.parameters(List.of(new Parameter().key("visitDate").values(List.of("2026-09-15"))));
		final var cancelled = new Investigation().status("CANCELLED").created(CREATED).parameters(List.of(new Parameter().key("visitDate").values(List.of("2026-09-01"))));
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(cancelled, investigation));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new ErrandAttachment().id("other-id").fileName("Foto.jpg").created(COMPLETED.plusSeconds(1)),
			new ErrandAttachment().id("scanned-id").fileName(FILE_NAME).created(COMPLETED.minusSeconds(1))));
		when(templatingIntegrationMock.renderPdf(MUNICIPALITY_ID, TEMPLATE, Map.of("visitDate", "2026-09-15"))).thenReturn(pdf);
		when(supportManagementIntegrationMock.createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, pdf)).thenReturn("attachment-id");

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE, FILE_NAME)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(templatingIntegrationMock).renderPdf(MUNICIPALITY_ID, TEMPLATE, Map.of("visitDate", "2026-09-15"));
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, FILE_NAME, pdf);
	}

	@ParameterizedTest
	@ValueSource(longs = {
		0, 1
	})
	void answersWithTheProtocolAnEarlierAttemptUploaded(final long secondsAfterCompleted) {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().status("COMPLETED").created(CREATED).modified(COMPLETED)));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("attachment-id").fileName(FILE_NAME).created(COMPLETED.plusSeconds(secondsAfterCompleted))));

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE, FILE_NAME)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void comparesWithTheCreationOfAnInvestigationNeverModified() {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Investigation().status("COMPLETED").created(COMPLETED)));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("attachment-id").fileName(FILE_NAME).created(COMPLETED)));

		assertThat(service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE, FILE_NAME)).isEqualTo("attachment-id");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"DRAFT", "ACTIVE", "CANCELLED"
	})
	void failsWithoutRetryWithoutACompletedInvestigation(final String status) {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Investigation().status(status)));

		assertThatThrownBy(() -> service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE, FILE_NAME))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no completed investigation to create a protocol from");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void failsWithoutRetryWithSeveralInvestigations() {
		when(supportManagementIntegrationMock.getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Investigation().status("COMPLETED"), new Investigation().status("COMPLETED")));

		assertThatThrownBy(() -> service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE, FILE_NAME))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has 2 completed investigations, the protocol is created from exactly one");

		verify(supportManagementIntegrationMock).getInvestigations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	@ParameterizedTest
	@CsvSource(value = {
		"null, Tillsynsprotokoll.pdf",
		"inspection.external.protocol, null",
		"' ', Tillsynsprotokoll.pdf"
	}, nullValues = "null")
	void failsWithoutRetryWhenTheStepLacksItsInputParameters(final String template, final String fileName) {
		assertThatThrownBy(() -> service.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, template, fileName))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The step has no protocol template or file name, see the input parameters in the bpmn schema");

		verifyNoInteractions(supportManagementIntegrationMock, templatingIntegrationMock);
	}
}
