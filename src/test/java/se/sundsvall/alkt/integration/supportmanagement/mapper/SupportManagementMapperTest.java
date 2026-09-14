package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.ProcessActivity;
import generated.se.sundsvall.supportmanagement.ProcessError;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.Test;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.api.model.ProcessStatus.FAILED;

class SupportManagementMapperTest {

	@Test
	void toReportTarget() {
		final var externalTask = mock(ExternalTask.class);
		final var errandId = UUID.randomUUID().toString();
		final var processInstanceId = UUID.randomUUID().toString();
		final var externalTaskId = UUID.randomUUID().toString();
		when(externalTask.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");
		when(externalTask.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn("ALKT");
		when(externalTask.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(errandId);
		when(externalTask.getProcessInstanceId()).thenReturn(processInstanceId);
		when(externalTask.getProcessDefinitionKey()).thenReturn("alcohol-serving");
		when(externalTask.getId()).thenReturn(externalTaskId);

		assertThat(SupportManagementMapper.toReportTarget(externalTask))
			.isEqualTo(new ReportTarget("2281", "ALKT", errandId, processInstanceId, "alcohol-serving", externalTaskId));
	}

	@Test
	void toErrandProcess() {
		final var externalTaskId = UUID.randomUUID().toString();
		final var target = new ReportTarget("2281", "ALKT", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "alcohol-serving", externalTaskId);
		final var occurredAt = OffsetDateTime.now();
		final var activity = new ProcessActivity().activityType("INCIDENT").activityId("investigation_phase").severity("ERROR").occurredAt(occurredAt);
		final var error = new ProcessError().code("INCIDENT").message("Timeout");
		final var report = new ProcessStateReport(FAILED, "investigation_phase", "Investigation", error, List.of(activity));

		final var result = SupportManagementMapper.toErrandProcess(target, report);

		assertThat(result.getProcessService()).isEqualTo("pw-alkt");
		assertThat(result.getProcessKey()).isEqualTo("alcohol-serving");
		assertThat(result.getProcessInstanceId()).isNull();
		assertThat(result.getProcessStatus()).isEqualTo("FAILED");
		assertThat(result.getCurrentActivityId()).isEqualTo("investigation_phase");
		assertThat(result.getCurrentActivityName()).isEqualTo("Investigation");
		assertThat(result.getExternalTaskId()).isEqualTo(externalTaskId);
		assertThat(result.getError()).isSameAs(error);
		assertThat(result.getActivities()).containsExactly(activity);
	}

	@Test
	void toErrandProcessFromACompletedReport() {
		final var target = new ReportTarget("2281", "ALKT", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "supervision", null);

		final var result = SupportManagementMapper.toErrandProcess(target, ProcessStateReport.completed());

		assertThat(result.getProcessStatus()).isEqualTo("COMPLETED");
		assertThat(result.getCurrentActivityId()).isNull();
		assertThat(result.getExternalTaskId()).isNull();
		assertThat(result.getError()).isNull();
		assertThat(result.getActivities()).isEmpty();
	}
}
