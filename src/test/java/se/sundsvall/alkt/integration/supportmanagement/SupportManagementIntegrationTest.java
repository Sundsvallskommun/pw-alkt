package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcesses;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportManagementIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = UUID.randomUUID().toString();
	private static final String PROCESS_INSTANCE_ID = UUID.randomUUID().toString();

	@Mock
	private SupportManagementClient supportManagementClientMock;

	@InjectMocks
	private SupportManagementIntegration supportManagementIntegration;

	@Test
	void reportProcessSendsTheReport() {
		final var report = new ErrandProcess();

		supportManagementIntegration.reportProcess(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, report);

		verify(supportManagementClientMock).reportProcess(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, report);
		verifyNoMoreInteractions(supportManagementClientMock);
	}

	@Test
	void getErrandProcessesAnswersWithTheRows() {
		final var row = new ErrandProcess().processInstanceId(PROCESS_INSTANCE_ID);
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(ResponseEntity.ok(new ErrandProcesses().processes(List.of(row))));

		assertThat(supportManagementIntegration.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).containsExactly(row);
	}

	@Test
	void getErrandProcessesAnswersWithNothingWithoutABody() {
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(null));

		assertThat(supportManagementIntegration.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

	@Test
	void getErrandProcessesAnswersWithNothingWhenTheBodyCarriesNoRows() {
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(new ErrandProcesses()));

		assertThat(supportManagementIntegration.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

	@Test
	void getAttachmentsAnswersWithTheListing() {
		final var attachment = new ErrandAttachment().fileName("beslut.pdf");
		when(supportManagementClientMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(List.of(attachment)));

		assertThat(supportManagementIntegration.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).containsExactly(attachment);
	}

	@Test
	void getAttachmentsAnswersWithNothingWithoutABody() {
		when(supportManagementClientMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(null));

		assertThat(supportManagementIntegration.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

	@Test
	void getAttachmentAnswersWithTheBytes() {
		final var attachmentId = UUID.randomUUID().toString();
		final var content = "file".getBytes();
		when(supportManagementClientMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, attachmentId)).thenReturn(ResponseEntity.ok(content));

		assertThat(supportManagementIntegration.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, attachmentId)).isEqualTo(content);
	}

	@Test
	void getAttachmentFailsWhenTheFileCarriesNoContent() {
		final var attachmentId = UUID.randomUUID().toString();
		when(supportManagementClientMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, attachmentId)).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> supportManagementIntegration.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, attachmentId))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("came back without content");
	}

	@Test
	void getErrandAnswersWithTheErrand() {
		final var errand = new Errand().id(ERRAND_ID);
		when(supportManagementClientMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(errand));

		assertThat(supportManagementIntegration.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isSameAs(errand);
	}

	@Test
	void getErrandFailsWithoutABody() {
		when(supportManagementClientMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> supportManagementIntegration.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("came back without content");
	}

	@Test
	void getLatestCompletedDecisionPicksTheMostRecentlyDecidedCompletedOne() {
		final var older = new Decision().status("COMPLETED").decidedAt(OffsetDateTime.parse("2026-09-01T10:00:00+02:00"));
		final var newer = new Decision().status("COMPLETED").decidedAt(OffsetDateTime.parse("2026-09-20T10:00:00+02:00"));
		final var undated = new Decision().status("COMPLETED");
		final var ongoing = new Decision().status("ONGOING").decidedAt(OffsetDateTime.parse("2026-09-22T10:00:00+02:00"));
		when(supportManagementClientMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(List.of(older, ongoing, undated, newer)));

		assertThat(supportManagementIntegration.getLatestCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).containsSame(newer);
	}

	@Test
	void getLatestCompletedDecisionAnswersWithNothingWithoutACompletedDecision() {
		when(supportManagementClientMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(List.of(new Decision().status("ONGOING"))));

		assertThat(supportManagementIntegration.getLatestCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

	@Test
	void getLatestCompletedDecisionAnswersWithNothingWithoutABody() {
		when(supportManagementClientMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(null));

		assertThat(supportManagementIntegration.getLatestCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

}
