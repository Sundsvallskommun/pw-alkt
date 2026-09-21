package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcesses;
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

	/** The decision of a process must not wake that same process, so the trigger is turned off. */
	@Test
	void createDecisionDoesNotTriggerTheProcess() {
		final var decision = new Decision().method("AUTOMATIC");

		supportManagementIntegration.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, decision);

		verify(supportManagementClientMock).createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, false, decision);
		verifyNoMoreInteractions(supportManagementClientMock);
	}

}
