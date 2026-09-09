package se.sundsvall.alkt.service;

import generated.se.sundsvall.operaton.ProcessInstanceDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.dept44.exception.ClientProblem;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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
	private OperatonIntegration operatonIntegrationMock;

	@InjectMocks
	private ProcessService processService;

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

		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of());
		when(operatonIntegrationMock.startProcess(MUNICIPALITY_ID, NAMESPACE, errandId, PROCESS_KEY, TENANT_ID)).thenReturn(processInstanceId);

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(CREATE, errandId, PROCESS_KEY, true));

		// Assert
		verify(operatonIntegrationMock).startProcess(MUNICIPALITY_ID, NAMESPACE, errandId, PROCESS_KEY, TENANT_ID);
	}

	/**
	 * The label of an errand can be set in a second call, so the key arrives with an UPDATE rather than a CREATE. Starting
	 * only on CREATE would leave that errand without a process for good.
	 */
	@Test
	void startsProcessOnUpdateWhenTheKeyArrivesLate() {

		// Arrange
		final var errandId = randomUUID().toString();

		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of());
		when(operatonIntegrationMock.startProcess(any(), any(), any(), any(), any())).thenReturn(randomUUID().toString());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, true));

		// Assert
		verify(operatonIntegrationMock).startProcess(MUNICIPALITY_ID, NAMESPACE, errandId, PROCESS_KEY, TENANT_ID);
	}

	@Test
	void startsNothingWhenTheErrandCarriesNoProcessKey() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonIntegrationMock.findProcessInstances(errandId, null, TENANT_ID)).thenReturn(List.of());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, null, true));

		// Assert
		verify(operatonIntegrationMock).findProcessInstances(errandId, null, TENANT_ID);
		verifyNoMoreInteractions(operatonIntegrationMock);
	}

	@Test
	void startsNothingWhenSupportManagementWithholdsThePermission() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, false));

		// Assert
		verify(operatonIntegrationMock).findProcessInstances(errandId, PROCESS_KEY, TENANT_ID);
		verifyNoMoreInteractions(operatonIntegrationMock);
	}

	/** An absent permission is no permission: a process started in error spends the one process life the errand has. */
	@Test
	void readsAnAbsentPermissionAsNoPermission() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, null));

		// Assert
		verify(operatonIntegrationMock).findProcessInstances(errandId, PROCESS_KEY, TENANT_ID);
		verifyNoMoreInteractions(operatonIntegrationMock);
	}

	@Test
	void rejectsAProcessKeyThatIsNotDeployed() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonIntegrationMock.findProcessInstances(errandId, "no-such-process", TENANT_ID)).thenReturn(List.of());

		// Act
		final var problem = assertThrows(se.sundsvall.dept44.problem.ThrowableProblem.class,
			() -> processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, "no-such-process", true)));

		// Assert
		assertThat(problem.getStatus().value()).isEqualTo(422);
		verify(operatonIntegrationMock, never()).startProcess(any(), any(), any(), any(), any());
	}

	@Test
	void correlatesTheGenericMessageForAnOrdinaryChange() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, false));

		// Assert
		verify(operatonIntegrationMock).correlateMessage("errandUpdated", errandId, TENANT_ID);
	}

	@Test
	void correlatesTheNamedGateForASignal() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var errandEvent = event(UPDATE, errandId, PROCESS_KEY, false);
		errandEvent.setEventSubType("SIGNAL");
		errandEvent.setSignalName("review_completed");

		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, errandEvent);

		// Assert
		verify(operatonIntegrationMock).correlateMessage("review_completed", errandId, TENANT_ID);
	}

	/** Support Management's casing of eventSubType is not guaranteed, so the SIGNAL check must not be case-sensitive. */
	@Test
	void correlatesTheNamedGateForASignalRegardlessOfCase() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var errandEvent = event(UPDATE, errandId, PROCESS_KEY, false);
		errandEvent.setEventSubType("signal");
		errandEvent.setSignalName("review_completed");

		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, errandEvent);

		// Assert
		verify(operatonIntegrationMock).correlateMessage("review_completed", errandId, TENANT_ID);
	}

	/** A signal without a name can never become correlatable, so redelivering it would be pointless. */
	@Test
	void acceptsASignalWithoutANameWithoutCorrelating() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var errandEvent = event(UPDATE, errandId, PROCESS_KEY, false);
		errandEvent.setEventSubType("SIGNAL");

		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, errandEvent);

		// Assert
		verify(operatonIntegrationMock, never()).correlateMessage(any(), any(), any());
	}

	/** The process was between two wait states when the change arrived. Ordinary, and not something a retry can mend. */
	@Test
	void acceptsAMessageThatMatchesNoWaitState() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonIntegrationMock.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(randomUUID().toString())));
		doThrow(new ClientProblem(BAD_GATEWAY, "No matching wait state")).when(operatonIntegrationMock).correlateMessage(any(), any(), any());

		// Act and assert
		assertThatNoException().isThrownBy(() -> processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(UPDATE, errandId, PROCESS_KEY, false)));
	}

	@Test
	void deletesTheProcessOfADeletedErrand() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var processInstanceId = randomUUID().toString();
		when(operatonIntegrationMock.findProcessInstances(errandId, null, TENANT_ID)).thenReturn(List.of(new ProcessInstanceDto().id(processInstanceId)));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(DELETE, errandId, null, false));

		// Assert
		verify(operatonIntegrationMock).findProcessInstances(errandId, null, TENANT_ID);
		verify(operatonIntegrationMock).deleteProcessInstance(processInstanceId);
		verifyNoMoreInteractions(operatonIntegrationMock);
	}

	/** The key of a deleted errand cannot always be resolved, so the instance is matched on its business key alone. */
	@Test
	void deletesTheProcessEvenWhenTheEventCarriesNoKey() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var errandEvent = event(DELETE, errandId, "some-key", false);
		when(operatonIntegrationMock.findProcessInstances(eq(errandId), isNull(), eq(TENANT_ID))).thenReturn(List.of(new ProcessInstanceDto().id("instance")));

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, errandEvent);

		// Assert
		verify(operatonIntegrationMock).deleteProcessInstance("instance");
	}

	@Test
	void acceptsADeleteForAnErrandWithoutAProcess() {

		// Arrange
		final var errandId = randomUUID().toString();
		when(operatonIntegrationMock.findProcessInstances(errandId, null, TENANT_ID)).thenReturn(List.of());

		// Act
		processService.handleErrandEvent(MUNICIPALITY_ID, NAMESPACE, event(DELETE, errandId, null, false));

		// Assert
		verify(operatonIntegrationMock).findProcessInstances(errandId, null, TENANT_ID);
		verifyNoMoreInteractions(operatonIntegrationMock);
	}
}
