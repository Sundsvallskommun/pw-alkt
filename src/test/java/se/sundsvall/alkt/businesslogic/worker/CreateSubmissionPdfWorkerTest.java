package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.exception.StepSkippedException;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.SubmissionPdfService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.SubmissionPdfOutcome;
import se.sundsvall.dept44.exception.ClientProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;

@ExtendWith(MockitoExtension.class)
class CreateSubmissionPdfWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String ACTIVITY_ID = "external_task_create_submission_pdf";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private SubmissionPdfService submissionPdfServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private CreateSubmissionPdfWorker worker;

	@AfterEach
	void verifyNoUnexpectedInteractions() {
		verifyNoMoreInteractions(submissionPdfServiceMock, failureHandlerMock);
	}

	private void givenTheErrand() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
	}

	@Test
	void aCreatedPdfIsReportedDone() {
		givenTheErrand();
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(submissionPdfServiceMock.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(SubmissionPdfOutcome.CREATED);

		assertThat(worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock))
			.isEqualTo(ProcessStateReport.running(ACTIVITY_ID, null).withLogMessage("Submission PDF created and attached to the errand"));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
		verify(submissionPdfServiceMock).createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void aPdfAlreadyOnTheErrandIsReportedDone() {
		givenTheErrand();
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(submissionPdfServiceMock.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(SubmissionPdfOutcome.ALREADY_EXISTS);

		assertThat(worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock))
			.isEqualTo(ProcessStateReport.running(ACTIVITY_ID, null).withLogMessage("Submission PDF already on the errand, not created again"));
		verify(submissionPdfServiceMock).createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void anErrandWithoutSubmissionSkipsTheStep() {
		givenTheErrand();
		when(submissionPdfServiceMock.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(SubmissionPdfOutcome.SKIPPED);

		assertThatThrownBy(() -> worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock))
			.isInstanceOf(StepSkippedException.class)
			.hasMessage("Errand 'errand-id' has no JSON parameters, so there is no submission to make a PDF of");
		verify(submissionPdfServiceMock).createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void anErrandWithoutSubmissionIsTakenPastTheStepWithoutIncident() {
		givenTheErrand();
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_REQUEST_ID)).thenReturn("request-id");
		when(submissionPdfServiceMock.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(SubmissionPdfOutcome.SKIPPED);
		when(failureHandlerMock.handleSkipped(eq(externalTaskServiceMock), eq(externalTaskMock), contains("no JSON parameters"))).thenReturn(true);

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(failureHandlerMock).handleSkipped(eq(externalTaskServiceMock), eq(externalTaskMock), contains("no JSON parameters"));
		verify(failureHandlerMock, never()).handleIncident(any(), any(), anyString());
		verify(failureHandlerMock, never()).handleException(any(), any(), anyString());
		verify(externalTaskServiceMock, never()).complete(any(), any());
		verify(submissionPdfServiceMock).createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void aServiceThatDoesNotAnswerIsRetried() {
		givenTheErrand();
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_REQUEST_ID)).thenReturn("request-id");
		when(submissionPdfServiceMock.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(new ClientProblem(BAD_GATEWAY, "templating is down"));

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(failureHandlerMock).handleException(eq(externalTaskServiceMock), eq(externalTaskMock), anyString());
		verify(failureHandlerMock, never()).handleIncident(any(), any(), anyString());
		verify(externalTaskServiceMock, never()).complete(any(), any());
		verify(submissionPdfServiceMock).createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void aSchemaThatDoesNotExistIsAnIncidentAtOnce() {
		givenTheErrand();
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_REQUEST_ID)).thenReturn("request-id");
		when(submissionPdfServiceMock.createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(new NonRetryableException("Schema 'x' does not exist"));

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(failureHandlerMock).handleIncident(eq(externalTaskServiceMock), eq(externalTaskMock), contains("Schema 'x' does not exist"));
		verify(failureHandlerMock, never()).handleException(any(), any(), anyString());
		verify(submissionPdfServiceMock).createSubmissionPdf(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}
}
