package se.sundsvall.alkt.integration.operaton;

import generated.se.sundsvall.operaton.CorrelationMessageDto;
import generated.se.sundsvall.operaton.EventSubscriptionDto;
import generated.se.sundsvall.operaton.ExecutionDto;
import generated.se.sundsvall.operaton.MessageCorrelationResultWithVariableDto;
import generated.se.sundsvall.operaton.ProcessInstanceDto;
import generated.se.sundsvall.operaton.ProcessInstanceWithVariablesDto;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import generated.se.sundsvall.operaton.VariableValueDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.integration.operaton.ProcessModelCache.ProcessModel;
import se.sundsvall.alkt.service.model.AwaitingSignal;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static generated.se.sundsvall.operaton.MessageCorrelationResultWithVariableDto.ResultTypeEnum.EXECUTION;
import static generated.se.sundsvall.operaton.MessageCorrelationResultWithVariableDto.ResultTypeEnum.PROCESS_DEFINITION;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatonIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String TENANT_ID = "ALKT";
	private static final String PROCESS_KEY = "alcohol-serving";
	private static final String DEFINITION_ID = "alcohol-serving:1:3c3755ad-b1a7-11f1-af7f-7aca4f79b75a";

	@Mock
	private OperatonClient operatonClientMock;

	@Mock
	private ProcessModelCache processModelCacheMock;

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
	void findProcessInstanceDelegatesToTheClient() {
		final var processInstanceId = randomUUID().toString();
		final var instance = new ProcessInstanceDto().id(processInstanceId);
		when(operatonClientMock.getProcessInstance(processInstanceId)).thenReturn(Optional.of(instance));

		assertThat(operatonIntegration.findProcessInstance(processInstanceId)).contains(instance);
	}

	/** dismiss404 on the client, so an instance that ended comes back as an empty answer rather than as a failure. */
	@Test
	void findProcessInstanceIsEmptyForAnInstanceThatIsGone() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getProcessInstance(processInstanceId)).thenReturn(Optional.empty());

		assertThat(operatonIntegration.findProcessInstance(processInstanceId)).isEmpty();
	}

	@Test
	void startProcessBuildsTheStartDtoAndReturnsTheNewInstance() {
		final var errandId = randomUUID().toString();
		final var processInstanceId = randomUUID().toString();
		final var logId = randomUUID().toString();
		when(operatonClientMock.startProcessWithTenant(eq(PROCESS_KEY), eq(TENANT_ID), any())).thenReturn(new ProcessInstanceWithVariablesDto().id(processInstanceId));

		final ProcessInstanceWithVariablesDto result;
		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			requestIdMock.when(RequestId::get).thenReturn(logId);
			result = operatonIntegration.startProcess(MUNICIPALITY_ID, NAMESPACE, errandId, PROCESS_KEY, TENANT_ID);
		}

		assertThat(result.getId()).isEqualTo(processInstanceId);

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
		when(operatonClientMock.correlateMessage(any())).thenReturn(List.of());

		operatonIntegration.correlateMessage("review_completed", errandId, TENANT_ID);

		final var correlationMessageCaptor = ArgumentCaptor.forClass(CorrelationMessageDto.class);
		verify(operatonClientMock).correlateMessage(correlationMessageCaptor.capture());
		assertThat(correlationMessageCaptor.getValue())
			.extracting(CorrelationMessageDto::getMessageName, CorrelationMessageDto::getBusinessKey, CorrelationMessageDto::getTenantId, CorrelationMessageDto::getAll,
				CorrelationMessageDto::getResultEnabled)
			.containsExactly("review_completed", errandId, TENANT_ID, false, true);
	}

	/** An intermediate catch event answers with the execution the message reached, not with the process instance. */
	@Test
	void correlateMessageReturnsTheInstanceOfTheExecutionItReached() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.correlateMessage(any())).thenReturn(List.of(new MessageCorrelationResultWithVariableDto()
			.resultType(EXECUTION)
			.execution(new ExecutionDto().id(randomUUID().toString()).processInstanceId(processInstanceId))));

		assertThat(operatonIntegration.correlateMessage("review_completed", randomUUID().toString(), TENANT_ID)).contains(processInstanceId);
	}

	/** A message start event answers with the instance it started instead. */
	@Test
	void correlateMessageReturnsTheInstanceAMessageStartEventCreated() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.correlateMessage(any())).thenReturn(List.of(new MessageCorrelationResultWithVariableDto()
			.resultType(PROCESS_DEFINITION)
			.processInstance(new ProcessInstanceDto().id(processInstanceId))));

		assertThat(operatonIntegration.correlateMessage("review_completed", randomUUID().toString(), TENANT_ID)).contains(processInstanceId);
	}

	@Test
	void correlateMessageReturnsNothingWhenTheAnswerNamesNoInstance() {
		when(operatonClientMock.correlateMessage(any())).thenReturn(List.of());

		assertThat(operatonIntegration.correlateMessage("review_completed", randomUUID().toString(), TENANT_ID)).isEmpty();
	}

	@Test
	void correlateMessagePropagatesAFailure() {
		when(operatonClientMock.correlateMessage(any())).thenThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "No matching wait state"));

		assertThatThrownBy(() -> operatonIntegration.correlateMessage("review_completed", randomUUID().toString(), TENANT_ID))
			.isInstanceOf(ClientProblem.class);
	}

	@Test
	void findWaitStateNamesThePhaseAndTheSignalsOfTheSubscriptions() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message"))
			.thenReturn(List.of(subscription("await_review_completed", "review_completed")));
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(new ProcessModel(
			Map.of("await_review_completed", "Review completed", "review_phase", "Review"),
			Map.of("await_review_completed", "review_phase")));

		final var waitState = operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID);

		assertThat(waitState).hasValueSatisfying(state -> {
			assertThat(state.activityId()).isEqualTo("review_phase");
			assertThat(state.activityName()).isEqualTo("Review");
			assertThat(state.awaitingSignals())
				.extracting(AwaitingSignal::name, AwaitingSignal::label)
				.containsExactly(tuple("review_completed", "Review completed"));
		});
	}

	/**
	 * The phase and the buttons reported with it have to describe the same place, so a wakeup subscription elsewhere in
	 * the model must not decide the phase the gate sits in.
	 */
	@Test
	void findWaitStateTakesThePhaseFromAGateRatherThanFromTheWakeupSubscription() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message"))
			.thenReturn(List.of(subscription("await_errand_updated", "errandUpdated"), subscription("await_review_completed", "review_completed")));
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(new ProcessModel(
			Map.of("await_review_completed", "Review completed", "review_phase", "Review", "closure_phase", "Closure"),
			Map.of("await_errand_updated", "closure_phase", "await_review_completed", "review_phase")));

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).hasValueSatisfying(state -> {
			assertThat(state.activityId()).isEqualTo("review_phase");
			assertThat(state.activityName()).isEqualTo("Review");
			assertThat(state.awaitingSignals()).extracting(AwaitingSignal::name).containsExactly("review_completed");
		});
	}

	/** An automatic wait state: the process waits, but for no one a case worker can answer for. */
	@Test
	void findWaitStateLeavesErrandUpdatedOutOfTheSignals() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message"))
			.thenReturn(List.of(subscription("await_errand_updated", "errandUpdated")));
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(new ProcessModel(
			Map.of("review_phase", "Review"),
			Map.of("await_errand_updated", "review_phase")));

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).hasValueSatisfying(state -> {
			assertThat(state.activityId()).isEqualTo("review_phase");
			assertThat(state.awaitingSignals()).isEmpty();
		});
	}

	@Test
	void findWaitStateLeavesDecisionUpdatedOutOfTheSignals() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message"))
			.thenReturn(List.of(subscription("await_decision_updated", "decision_updated")));
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(new ProcessModel(
			Map.of("decision_phase", "Decision"),
			Map.of("await_decision_updated", "decision_phase")));

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).hasValueSatisfying(state -> {
			assertThat(state.activityId()).isEqualTo("decision_phase");
			assertThat(state.awaitingSignals()).isEmpty();
		});
	}

	/** A model that says nothing about the activity still gives a report, with the message name as the button text. */
	@Test
	void findWaitStateFallsBackToTheMessageName() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message"))
			.thenReturn(List.of(subscription("await_review_completed", "review_completed")));
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(ProcessModel.EMPTY);

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).hasValueSatisfying(state -> {
			assertThat(state.activityId()).isEqualTo("await_review_completed");
			assertThat(state.activityName()).isNull();
			assertThat(state.awaitingSignals()).extracting(AwaitingSignal::label).containsExactly("review_completed");
		});
	}

	/** Id and name describe the same element or neither, so an unnamed phase must not borrow the catch event's name. */
	@Test
	void findWaitStateLeavesTheNameOutForAPhaseTheModelDoesNotName() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message"))
			.thenReturn(List.of(subscription("await_review_completed", "review_completed")));
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(new ProcessModel(
			Map.of("await_review_completed", "Review completed"),
			Map.of("await_review_completed", "review_phase")));

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).hasValueSatisfying(state -> {
			assertThat(state.activityId()).isEqualTo("review_phase");
			assertThat(state.activityName()).isNull();
		});
	}

	/** Support Management refuses a signal without a name, and that answer would cost the whole report. */
	@Test
	void findWaitStateLeavesANamelessSubscriptionOutOfTheSignals() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message"))
			.thenReturn(List.of(subscription("await_review_completed", null), subscription("await_review_completed", "review_completed")));
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(ProcessModel.EMPTY);

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).hasValueSatisfying(state -> assertThat(state.awaitingSignals())
			.extracting(AwaitingSignal::name)
			.containsExactly("review_completed"));
	}

	/** The engine answers in no defined order, so an unchanged wait state would otherwise report a different gate. */
	@Test
	void findWaitStateReportsTheSameGateWhateverOrderTheEngineAnswersIn() {
		final var processInstanceId = randomUUID().toString();
		final var gateway = List.of(subscription("await_rejection", "rejected"), subscription("await_approval", "approved"));
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message")).thenReturn(gateway.reversed(), gateway);
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(ProcessModel.EMPTY);

		final var first = operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID);
		final var second = operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID);

		assertThat(first)
			.contains(second.orElseThrow())
			.hasValueSatisfying(state -> {
				assertThat(state.activityId()).isEqualTo("await_approval");
				assertThat(state.awaitingSignals()).extracting(AwaitingSignal::name).containsExactly("approved", "rejected");
			});
	}

	@Test
	void findWaitStateIsEmptyForAnInstanceWaitingForNoMessage() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message")).thenReturn(List.of());

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).isEmpty();
		verifyNoInteractions(processModelCacheMock);
	}

	@Test
	void deleteProcessInstanceNeverFailsOnAMissingInstance() {
		final var processInstanceId = randomUUID().toString();

		operatonIntegration.deleteProcessInstance(processInstanceId);

		verify(operatonClientMock).deleteProcessInstance(processInstanceId, false);
	}

	/**
	 * The engine may answer with a subscription without an activity id, and that must cost that one, not the wait state.
	 */
	@Test
	void findWaitStateLeavesOutASubscriptionWithoutAnActivityId() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message"))
			.thenReturn(List.of(subscription(null, "nameless"), subscription("await_review_completed", "review_completed")));
		when(processModelCacheMock.modelOf(DEFINITION_ID)).thenReturn(new ProcessModel(
			Map.of("await_review_completed", "Review completed", "review_phase", "Review"),
			Map.of("await_review_completed", "review_phase")));

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).hasValueSatisfying(state -> {
			assertThat(state.activityId()).isEqualTo("review_phase");
			assertThat(state.activityName()).isEqualTo("Review");
			assertThat(state.awaitingSignals()).extracting(AwaitingSignal::name).containsExactly("review_completed");
		});
	}

	/** A wait state made up only of such subscriptions reports nothing rather than failing. */
	@Test
	void findWaitStateIsEmptyWhenNoSubscriptionNamesAnActivity() {
		final var processInstanceId = randomUUID().toString();
		when(operatonClientMock.getEventSubscriptions(processInstanceId, "message")).thenReturn(List.of(subscription(null, "nameless")));

		assertThat(operatonIntegration.findWaitState(processInstanceId, DEFINITION_ID)).isEmpty();
	}

	private static EventSubscriptionDto subscription(final String activityId, final String eventName) {
		return new EventSubscriptionDto().activityId(activityId).eventName(eventName).eventType("message");
	}
}
