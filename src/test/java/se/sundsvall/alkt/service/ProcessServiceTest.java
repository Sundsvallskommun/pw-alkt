package se.sundsvall.alkt.service;

import generated.se.sundsvall.operaton.CorrelationMessageDto;
import generated.se.sundsvall.operaton.ProcessInstanceDto;
import generated.se.sundsvall.operaton.ProcessInstanceWithVariablesDto;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import generated.se.sundsvall.operaton.VariableValueDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.CREATE;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.DELETE;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.UPDATE;

@ExtendWith(MockitoExtension.class)
class ProcessServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String TENANT_ID = "ALKT";
	private static final String PROCESS_KEY = "alcohol-serving";

	@Mock
	private OperatonClient operatonClientMock;

	@InjectMocks
	private ProcessService processService;

	@Captor
	private ArgumentCaptor<StartProcessInstanceDto> startProcessCaptor;

	@Captor
	private ArgumentCaptor<CorrelationMessageDto> correlationMessageCaptor;

	private static ErrandEvent event(final ErrandEvent.EventType eventType, final String errandId, final String processKey, final Boolean startAllowed) {
		final var errandEvent = new ErrandEvent();
		errandEvent.setEventId(randomUUID().toString());
		errandEvent.setEventType(eventType);
		errandEvent.setEventSubType("ERRAND");
		errandEvent.setErrandId(errandId);
		errandEvent.setProcessKey(processKey);
		if (startAllowed != null) {
			errandEvent.setStartAllowed(startAllowed);
		}
		return errandEvent;
	}

	@Test
	void startsProcessWhenNothingIsRunningAndStartIsAllowed() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var processInstanceId = randomUUID().toString();
		final var logId = randomUUID().toString();

		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of());
		when(operatonClientMock.startProcessWithTenant(any(), any(), any())).thenReturn(new ProcessInstanceWithVariablesDto().id(processInstanceId));

		// Act
		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			requestIdMock.when(RequestId::get).thenReturn(logId);
			processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(CREATE, errandId, PROCESS_KEY, true));
		}

		// Assert
		verify(operatonClientMock).startProcessWithTenant(eq(PROCESS_KEY), eq(TENANT_ID), startProcessCaptor.capture());
		assertThat(startProcessCaptor.getValue().getBusinessKey()).isEqualTo(errandId);
		assertThat(startProcessCaptor.getValue().getVariables())
			.containsKeys("municipalityId", "namespace", "errandId", "requestId")
			.extractingByKeys("municipalityId", "namespace", "errandId", "requestId")
			.extracting(VariableValueDto::getValue)
			.containsExactly(MUNICIPALITY_ID, NAMESPACE, errandId, logId);
	}

	/**
	 * The label of an errand can be set in a second call, so the key arrives with an UPDATE rather than a CREATE. Starting
	 * only on CREATE would leave that errand without a process for good.
	 */
	@Test
	void startsProcessOnUpdateWhenTheKeyArrivesLate() {

		// Arrange
		final var errandId = randomUUID().toString();

		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of());
		when(operatonClientMock.startProcessWithTenant(any(), any(), any())).thenReturn(new ProcessInstanceWithVariablesDto().id(randomUUID().toString()));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, true));

		// Assert
		verify(operatonClientMock).startProcessWithTenant(eq(PROCESS_KEY), eq(TENANT_ID), any());
	}

	@Test
	void startsNothingWhenTheErrandCarriesNoProcessKey() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonClientMock.findProcessInstances(errandId, null, TENANT_ID)).thenReturn(List.of());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, null, true));

		// Assert
		verify(operatonClientMock).findProcessInstances(errandId, null, TENANT_ID);
		verifyNoMoreInteractions(operatonClientMock);
	}

	@Test
	void startsNothingWhenSupportManagementWithholdsThePermission() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, false));

		// Assert
		verify(operatonClientMock).findProcessInstances(errandId, PROCESS_KEY, TENANT_ID);
		verifyNoMoreInteractions(operatonClientMock);
	}

	/** An absent permission is no permission: a process started in error spends the one process life the errand has. */
	@Test
	void readsAnAbsentPermissionAsNoPermission() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, null));

		// Assert
		verify(operatonClientMock).findProcessInstances(errandId, PROCESS_KEY, TENANT_ID);
		verifyNoMoreInteractions(operatonClientMock);
	}

	@Test
	void rejectsAProcessKeyThatIsNotDeployed() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonClientMock.findProcessInstances(errandId, "no-such-process", TENANT_ID)).thenReturn(List.of());

		// Act
		final var problem = assertThrows(se.sundsvall.dept44.problem.ThrowableProblem.class,
			() -> processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, "no-such-process", true)));

		// Assert
		assertThat(problem.getStatus().value()).isEqualTo(422);
		verify(operatonClientMock, never()).startProcessWithTenant(any(), any(), any());
	}

	@Test
	void correlatesTheGenericMessageForAnOrdinaryChange() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, false));

		// Assert
		verify(operatonClientMock).correlateMessage(correlationMessageCaptor.capture());
		assertThat(correlationMessageCaptor.getValue())
			.extracting(CorrelationMessageDto::getMessageName, CorrelationMessageDto::getBusinessKey, CorrelationMessageDto::getTenantId, CorrelationMessageDto::getAll)
			.containsExactly("errandUpdated", errandId, TENANT_ID, false);
	}

	@Test
	void correlatesTheNamedGateForASignal() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var errandEvent = event(UPDATE, errandId, PROCESS_KEY, false);
		errandEvent.setEventSubType("SIGNAL");
		errandEvent.setSignalName("review_completed");

		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, errandEvent);

		// Assert
		verify(operatonClientMock).correlateMessage(correlationMessageCaptor.capture());
		assertThat(correlationMessageCaptor.getValue())
			.extracting(CorrelationMessageDto::getMessageName, CorrelationMessageDto::getBusinessKey)
			.containsExactly("review_completed", errandId);
	}

	/** A signal without a name can never become correlatable, so redelivering it would be pointless. */
	@Test
	void acceptsASignalWithoutANameWithoutCorrelating() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var errandEvent = event(UPDATE, errandId, PROCESS_KEY, false);
		errandEvent.setEventSubType("SIGNAL");

		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, errandEvent);

		// Assert
		verify(operatonClientMock, never()).correlateMessage(any());
	}

	/** The process was between two wait states when the change arrived. Ordinary, and not something a retry can mend. */
	@Test
	void acceptsAMessageThatMatchesNoWaitState() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonClientMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));
		doThrow(new ClientProblem(BAD_GATEWAY, "No matching wait state")).when(operatonClientMock).correlateMessage(any());

		// Act and assert
		assertThatNoException().isThrownBy(() -> processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, false)));
	}

	@Test
	void deletesTheProcessOfADeletedErrand() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.findProcessInstances(errandId, null, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(processInstanceId)));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(DELETE, errandId, null, false));

		// Assert
		verify(operatonClientMock).findProcessInstances(errandId, null, TENANT_ID);
		verify(operatonClientMock).deleteProcessInstance(processInstanceId, false);
		verifyNoMoreInteractions(operatonClientMock);
	}

	/** The key of a deleted errand cannot always be resolved, so the instance is matched on its business key alone. */
	@Test
	void deletesTheProcessEvenWhenTheEventCarriesNoKey() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var errandEvent = event(DELETE, errandId, "some-key", false);
		when(operatonClientMock.findProcessInstances(eq(errandId), isNull(), eq(TENANT_ID))).thenReturn(List.of(new ProcessInstanceDto().id("instance")));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, errandEvent);

		// Assert
		verify(operatonClientMock).deleteProcessInstance("instance", false);
	}

	@Test
	void acceptsADeleteForAnErrandWithoutAProcess() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonClientMock.findProcessInstances(errandId, null, TENANT_ID)).thenReturn(List.of());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(DELETE, errandId, null, false));

		// Assert
		verify(operatonClientMock).findProcessInstances(errandId, null, TENANT_ID);
		verifyNoMoreInteractions(operatonClientMock);
	}
}
