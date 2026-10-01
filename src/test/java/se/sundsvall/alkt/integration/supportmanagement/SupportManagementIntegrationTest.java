package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Conversation;
import generated.se.sundsvall.supportmanagement.ConversationRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcessOverview;
import generated.se.sundsvall.supportmanagement.ErrandProcessReport;
import generated.se.sundsvall.supportmanagement.MessageRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.sundsvall.alkt.integration.supportmanagement.model.MessagePage;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;

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
		final var report = new ErrandProcessReport();

		supportManagementIntegration.reportProcess(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, report);

		verify(supportManagementClientMock).reportProcess(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, report);
		verifyNoMoreInteractions(supportManagementClientMock);
	}

	@Test
	void getErrandProcessesAnswersWithTheRows() {
		final var row = new ErrandProcess().processInstanceId(PROCESS_INSTANCE_ID);
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(ResponseEntity.ok(new ErrandProcessOverview().processes(List.of(row))));

		assertThat(supportManagementIntegration.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).containsExactly(row);
	}

	@Test
	void getErrandProcessesAnswersWithNothingWithoutABody() {
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(null));

		assertThat(supportManagementIntegration.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

	@Test
	void getErrandProcessesAnswersWithNothingWhenTheBodyCarriesNoRows() {
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(new ErrandProcessOverview()));

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
	void getCompletedDecisionAnswersWithTheCompletedDecision() {
		final var completed = new Decision().status("COMPLETED");
		when(supportManagementClientMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(List.of(completed)));

		assertThat(supportManagementIntegration.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).containsSame(completed);
	}

	@Test
	void getCompletedDecisionAnswersWithNothingWithoutACompletedDecision() {
		when(supportManagementClientMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(List.of(new Decision().status("ONGOING"))));

		assertThat(supportManagementIntegration.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

	@Test
	void getCompletedDecisionAnswersWithNothingWithoutABody() {
		when(supportManagementClientMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(null));

		assertThat(supportManagementIntegration.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

	@Test
	void getDecisionsAnswersWithEveryDecision() {
		final var draft = new Decision().status("DRAFT");
		when(supportManagementClientMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(List.of(draft)));

		assertThat(supportManagementIntegration.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).containsExactly(draft);
	}

	@Test
	void createDecisionAnswersWithTheIdWithoutWakingTheProcess() {
		final var decision = new Decision();
		final var headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, "https://support-management.example.com/2281/ALKT/errands/" + ERRAND_ID + "/decisions/decision-id");
		when(supportManagementClientMock.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, false, decision)).thenReturn(ResponseEntity.status(CREATED).headers(headers).build());

		assertThat(supportManagementIntegration.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, decision)).isEqualTo("decision-id");
	}

	@Test
	void updateDecisionSendsTheChangeWithoutWakingTheProcess() {
		final var decision = new Decision();

		supportManagementIntegration.updateDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "decision-id", decision);

		verify(supportManagementClientMock).updateDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "decision-id", false, decision);
		verifyNoMoreInteractions(supportManagementClientMock);
	}

	@Test
	void linkDecisionAttachmentLinksWithoutWakingTheProcess() {
		supportManagementIntegration.linkDecisionAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "decision-id", "attachment-id");

		verify(supportManagementClientMock).linkDecisionAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "decision-id", "attachment-id", false);
		verifyNoMoreInteractions(supportManagementClientMock);
	}

	@Test
	void getConversationsAnswersWithTheConversations() {
		final var conversation = new Conversation().id("conversation-id");
		when(supportManagementClientMock.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(List.of(conversation)));

		assertThat(supportManagementIntegration.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).containsExactly(conversation);
	}

	@Test
	void getConversationsAnswersWithNothingWithoutABody() {
		when(supportManagementClientMock.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(null));

		assertThat(supportManagementIntegration.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEmpty();
	}

	@Test
	void createConversationAnswersWithTheIdWithoutWakingTheProcess() {
		final var conversation = new ConversationRequest();
		final var headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, "https://support-management.example.com/2281/ALKT/errands/" + ERRAND_ID + "/communication/conversations/conversation-id");
		when(supportManagementClientMock.createConversation(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, false, conversation)).thenReturn(ResponseEntity.status(CREATED).headers(headers).build());

		assertThat(supportManagementIntegration.createConversation(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, conversation)).isEqualTo("conversation-id");
	}

	@Test
	void getConversationMessagesAnswersWithThePage() {
		final var page = new MessagePage(null, true);
		when(supportManagementClientMock.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "conversation-id", 1, 100)).thenReturn(ResponseEntity.ok(page));

		assertThat(supportManagementIntegration.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "conversation-id", 1, 100)).isSameAs(page);
	}

	@Test
	void getConversationMessagesFailsWithoutABody() {
		when(supportManagementClientMock.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "conversation-id", 0, 100)).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> supportManagementIntegration.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "conversation-id", 0, 100))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("came back without content");
	}

	@Test
	void createConversationMessageSendsTheMessageAsJsonWithoutWakingTheProcess() {
		supportManagementIntegration.createConversationMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "conversation-id", new MessageRequest().content("Hej"));

		verify(supportManagementClientMock).createConversationMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "conversation-id", false,
			"{\"content\":\"Hej\",\"attachmentIds\":[]}");
		verifyNoMoreInteractions(supportManagementClientMock);
	}
}
