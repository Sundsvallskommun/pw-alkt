package se.sundsvall.alkt.integration.operaton;

import generated.se.sundsvall.operaton.CorrelationMessageDto;
import generated.se.sundsvall.operaton.ProcessInstanceDto;
import generated.se.sundsvall.operaton.ProcessInstanceWithVariablesDto;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import generated.se.sundsvall.operaton.VariableValueDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatonIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String TENANT_ID = "ALKT";
	private static final String PROCESS_KEY = "alcohol-serving";

	@Mock
	private OperatonClient operatonClientMock;

	@InjectMocks
	private OperatonIntegration operatonIntegration;

	@Test
	void findProcessInstancesDelegatesToTheClient() {
		final var errandId = randomUUID().toString();
		final var instances = List.of(new ProcessInstanceDto().id(randomUUID().toString()));
		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(instances);

		assertThat(operatonIntegration.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).isEqualTo(instances);
	}

	@Test
	void startProcessBuildsTheStartDtoAndReturnsTheNewInstanceId() {
		final var errandId = randomUUID().toString();
		final var processInstanceId = randomUUID().toString();
		final var logId = randomUUID().toString();
		when(operatonClientMock.startProcessWithTenant(eq(PROCESS_KEY), eq(TENANT_ID), any())).thenReturn(new ProcessInstanceWithVariablesDto().id(processInstanceId));

		final String result;
		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			requestIdMock.when(RequestId::get).thenReturn(logId);
			result = operatonIntegration.startProcess(MUNICIPALITY_ID, NAMESPACE, errandId, PROCESS_KEY, TENANT_ID);
		}

		assertThat(result).isEqualTo(processInstanceId);

		final var startProcessCaptor = ArgumentCaptor.forClass(StartProcessInstanceDto.class);
		verify(operatonClientMock).startProcessWithTenant(eq(PROCESS_KEY), eq(TENANT_ID), startProcessCaptor.capture());
		assertThat(startProcessCaptor.getValue().getBusinessKey()).isEqualTo(errandId);
		assertThat(startProcessCaptor.getValue().getVariables())
			.containsKeys("municipalityId", "namespace", "errandId", "requestId")
			.extractingByKeys("municipalityId", "namespace", "errandId", "requestId")
			.extracting(VariableValueDto::getValue)
			.containsExactly(MUNICIPALITY_ID, NAMESPACE, errandId, logId);
	}

	@Test
	void correlateMessageBuildsTheCorrelationDto() {
		final var errandId = randomUUID().toString();

		operatonIntegration.correlateMessage("review_completed", errandId, TENANT_ID);

		final var correlationMessageCaptor = ArgumentCaptor.forClass(CorrelationMessageDto.class);
		verify(operatonClientMock).correlateMessage(correlationMessageCaptor.capture());
		assertThat(correlationMessageCaptor.getValue())
			.extracting(CorrelationMessageDto::getMessageName, CorrelationMessageDto::getBusinessKey, CorrelationMessageDto::getTenantId, CorrelationMessageDto::getAll)
			.containsExactly("review_completed", errandId, TENANT_ID, false);
	}

	@Test
	void correlateMessagePropagatesAFailure() {
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "No matching wait state")).when(operatonClientMock).correlateMessage(any());

		assertThatThrownBy(() -> operatonIntegration.correlateMessage("review_completed", randomUUID().toString(), TENANT_ID))
			.isInstanceOf(ClientProblem.class);
	}

	@Test
	void deleteProcessInstanceNeverFailsOnAMissingInstance() {
		final var processInstanceId = randomUUID().toString();

		operatonIntegration.deleteProcessInstance(processInstanceId);

		verify(operatonClientMock).deleteProcessInstance(processInstanceId, false);
	}
}
