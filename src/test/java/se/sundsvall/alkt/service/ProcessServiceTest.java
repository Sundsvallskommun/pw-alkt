package se.sundsvall.alkt.service;

import generated.se.sundsvall.operaton.PatchVariablesDto;
import generated.se.sundsvall.operaton.ProcessInstanceDto;
import generated.se.sundsvall.operaton.ProcessInstanceWithVariablesDto;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import generated.se.sundsvall.operaton.VariableValueDto;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.dept44.requestid.RequestId;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessServiceTest {

	@Mock
	private OperatonClient operatonClientMock;

	@InjectMocks
	private ProcessService processService;

	@Captor
	private ArgumentCaptor<StartProcessInstanceDto> startProcessArgumentCaptor;

	@Captor
	private ArgumentCaptor<PatchVariablesDto> patchVariablesCaptor;

	@Test
	void startProcess() {

		// Arrange
		final var process = "alkt-ansokan";
		final var tenant = "ALKT";
		final var municipalityId = "2281";
		final var namespace = "ALKT";
		final var errandId = randomUUID().toString();
		final var uuid = randomUUID().toString();
		final var logId = randomUUID().toString();
		final var processInstance = new ProcessInstanceWithVariablesDto().id(uuid);

		when(operatonClientMock.startProcessWithTenant(any(), any(), any())).thenReturn(processInstance);

		// Mock static RequestId to enable spy and to verify that static method is being called
		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			requestIdMock.when(RequestId::get).thenReturn(logId);

			// Act
			assertThat(processService.startProcess(municipalityId, namespace, errandId)).isEqualTo(uuid);
		}

		// Assert
		verify(operatonClientMock).startProcessWithTenant(eq(process), eq(tenant), startProcessArgumentCaptor.capture());
		verifyNoMoreInteractions(operatonClientMock);
		assertThat(startProcessArgumentCaptor.getValue().getBusinessKey()).isEqualTo(errandId);
		assertThat(startProcessArgumentCaptor.getValue().getVariables()).hasSize(4)
			.containsKeys("municipalityId", "namespace", "errandId", "requestId")
			.extractingByKeys("municipalityId", "namespace", "errandId", "requestId")
			.extracting(VariableValueDto::getType, VariableValueDto::getValue)
			.contains(
				tuple(ValueType.STRING.getName(), municipalityId),
				tuple(ValueType.STRING.getName(), namespace),
				tuple(ValueType.STRING.getName(), errandId),
				tuple(ValueType.STRING.getName(), logId));
	}

	/**
	 * The upcoming anmalan and tillsyn processes are started through the same plumbing, with their own process key.
	 */
	@Test
	void startProcessWithExplicitProcessKey() {

		// Arrange
		final var process = "alkt-anmalan";
		final var tenant = "ALKT";
		final var municipalityId = "2281";
		final var namespace = "ALKT";
		final var errandId = randomUUID().toString();
		final var uuid = randomUUID().toString();

		when(operatonClientMock.startProcessWithTenant(any(), any(), any())).thenReturn(new ProcessInstanceWithVariablesDto().id(uuid));

		// Act
		final var result = processService.startProcess(process, municipalityId, namespace, errandId);

		// Assert
		assertThat(result).isEqualTo(uuid);
		verify(operatonClientMock).startProcessWithTenant(eq(process), eq(tenant), any());
		verifyNoMoreInteractions(operatonClientMock);
	}

	@Test
	void updateProcess() {

		// Arrange
		final var municipalityId = "2281";
		final var namespace = "ALKT";
		final var uuid = randomUUID().toString();
		final var logId = randomUUID().toString();

		when(operatonClientMock.getProcessInstance(any())).thenReturn(of(new ProcessInstanceDto()));

		// Mock static RequestId to enable spy and to verify that static method is being called
		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			requestIdMock.when(RequestId::get).thenReturn(logId);

			// Act
			processService.updateProcess(municipalityId, namespace, uuid);
		}

		// Assert
		verify(operatonClientMock).getProcessInstance(uuid);
		verify(operatonClientMock).setProcessInstanceVariables(eq(uuid), patchVariablesCaptor.capture());
		verifyNoMoreInteractions(operatonClientMock);
		assertThat(patchVariablesCaptor.getValue().getModifications()).hasSize(4)
			.containsKeys("municipalityId", "namespace", "updateAvailable", "requestId")
			.extractingByKeys("municipalityId", "namespace", "updateAvailable", "requestId")
			.extracting(VariableValueDto::getType, VariableValueDto::getValue)
			.contains(
				tuple(ValueType.STRING.getName(), municipalityId),
				tuple(ValueType.STRING.getName(), namespace),
				tuple(ValueType.BOOLEAN.getName(), true),
				tuple(ValueType.STRING.getName(), logId));
	}

	@Test
	void updateProcessNotFound() {

		// Arrange
		final var municipalityId = "2281";
		final var namespace = "ALKT";
		final var uuid = randomUUID().toString();

		when(operatonClientMock.getProcessInstance(any())).thenReturn(empty());

		// Act
		final var result = assertThrows(se.sundsvall.dept44.problem.ThrowableProblem.class, () -> processService.updateProcess(municipalityId, namespace, uuid));

		// Assert
		assertThat(result)
			.hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND)
			.hasFieldOrPropertyWithValue("detail", "Process instance with ID '%s' does not exist!".formatted(uuid));

		verify(operatonClientMock).getProcessInstance(uuid);
		verify(operatonClientMock, never()).setProcessInstanceVariables(any(), any());
		verifyNoMoreInteractions(operatonClientMock);
	}
}
