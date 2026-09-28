package se.sundsvall.alkt.integration.operaton;

import generated.se.sundsvall.operaton.EventSubscriptionDto;
import generated.se.sundsvall.operaton.ExecutionDto;
import generated.se.sundsvall.operaton.MessageCorrelationResultWithVariableDto;
import generated.se.sundsvall.operaton.ProcessInstanceDto;
import generated.se.sundsvall.operaton.ProcessInstanceWithVariablesDto;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.operaton.ProcessModelCache.ProcessModel;
import se.sundsvall.alkt.integration.operaton.mapper.OperatonMapper;
import se.sundsvall.alkt.service.model.AwaitingSignal;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.partitioningBy;
import static se.sundsvall.alkt.Constants.MESSAGE_DECISION_UPDATED;
import static se.sundsvall.alkt.Constants.MESSAGE_ERRAND_UPDATED;
import static se.sundsvall.alkt.Constants.MESSAGE_PROCESS_CANCELLED;

@Component
public class OperatonIntegration {

	// The subscriptions a manual gate produces. A timer or a signal subscription is not something a case worker answers.
	private static final String EVENT_TYPE_MESSAGE = "message";

	private static final Set<String> INTERNAL_MESSAGES = Set.of(MESSAGE_ERRAND_UPDATED, MESSAGE_DECISION_UPDATED);

	private final OperatonClient operatonClient;
	private final ProcessModelCache processModelCache;

	OperatonIntegration(final OperatonClient operatonClient, final ProcessModelCache processModelCache) {
		this.operatonClient = operatonClient;
		this.processModelCache = processModelCache;
	}

	public List<ProcessInstanceDto> findProcessInstances(final String errandId, final String processKey, final String tenantId) {
		return operatonClient.findProcessInstances(errandId, processKey, tenantId);
	}

	/** Empty for an instance that is no longer running, since the engine keeps no runtime row for one that ended. */
	public Optional<ProcessInstanceDto> findProcessInstance(final String processInstanceId) {
		return operatonClient.getProcessInstance(processInstanceId);
	}

	public ProcessInstanceWithVariablesDto startProcess(final String municipalityId, final String namespace, final String errandId, final String processKey, final String tenantId) {
		return operatonClient.startProcessWithTenant(processKey, tenantId, OperatonMapper.toStartProcessInstanceDto(municipalityId, namespace, errandId));
	}

	/** The correlation picks among the processes of an errand on the subscription, not on the key the event carried. */
	public Optional<String> correlateMessage(final String messageName, final String errandId, final String tenantId) {
		return operatonClient.correlateMessage(OperatonMapper.toCorrelationMessageDto(messageName, errandId, tenantId)).stream()
			.findFirst()
			.map(OperatonIntegration::processInstanceIdOf);
	}

	/** An intermediate catch event answers with the execution, a message start event with the instance. */
	private static String processInstanceIdOf(final MessageCorrelationResultWithVariableDto result) {
		return Optional.ofNullable(result.getExecution())
			.map(ExecutionDto::getProcessInstanceId)
			.orElseGet(() -> Optional.ofNullable(result.getProcessInstance())
				.map(ProcessInstanceDto::getId)
				.orElse(null));
	}

	public void deleteProcessInstance(final String processInstanceId) {
		operatonClient.deleteProcessInstance(processInstanceId, false);
	}

	/**
	 * Empty when the instance waits for no message of its own, which means it ended or stands on a work step. The
	 * cancellation is offered as the last button but never settles the phase.
	 */
	public Optional<WaitState> findWaitState(final String processInstanceId, final String processDefinitionId) {
		final var byCancellation = messageSubscriptionsOf(processInstanceId).stream()
			.collect(partitioningBy(OperatonIntegration::isCancellation));
		final var ownSubscriptions = byCancellation.get(false);
		if (ownSubscriptions.isEmpty()) {
			return Optional.empty();
		}

		final var model = processModelCache.modelOf(processDefinitionId);
		// A gate settles the phase, so that the phase and the buttons reported with it describe the same place. Every
		// alternative of an event-based gateway sits in the same phase, so the first gate is as good as any.
		final var ownGates = gatesOf(ownSubscriptions);
		final var activityId = ownGates.stream().findFirst().orElseGet(ownSubscriptions::getFirst).getActivityId();
		final var signals = Stream.concat(toSignals(ownGates, model).stream(), toSignals(byCancellation.get(true), model).stream()).toList();

		// Id and name are taken from the same element or from neither: a phase without a name in the model must not be
		// reported under the name of the catch event inside it.
		return Optional.of(model.phaseOf(activityId)
			.map(phase -> new WaitState(phase.id(), phase.name(), signals))
			.orElseGet(() -> new WaitState(activityId, model.labelOf(activityId, null), signals)));
	}

	/** The cancellation button, which listens in every phase, for a report from a work step. */
	public List<AwaitingSignal> findProcessWideSignals(final String processInstanceId, final String processDefinitionId) {
		final var cancellation = messageSubscriptionsOf(processInstanceId).stream()
			.filter(OperatonIntegration::isCancellation)
			.toList();
		if (cancellation.isEmpty()) {
			return List.of();
		}

		return toSignals(cancellation, processModelCache.modelOf(processDefinitionId));
	}

	private static boolean isCancellation(final EventSubscriptionDto subscription) {
		return MESSAGE_PROCESS_CANCELLED.equals(subscription.getEventName());
	}

	// Sorted because the engine answers in no defined order, and an unchanged wait state must report the same way twice.
	private List<EventSubscriptionDto> messageSubscriptionsOf(final String processInstanceId) {
		return operatonClient.getEventSubscriptions(processInstanceId, EVENT_TYPE_MESSAGE).stream()
			// A subscription without an activity id names neither a phase nor a button, and it costs the whole report if kept.
			.filter(subscription -> subscription.getActivityId() != null)
			.sorted(comparing(EventSubscriptionDto::getActivityId))
			.toList();
	}

	private static List<EventSubscriptionDto> gatesOf(final List<EventSubscriptionDto> subscriptions) {
		return subscriptions.stream()
			// A nameless signal is answered with 400, and that answer costs the whole wait state rather than one button.
			.filter(subscription -> subscription.getEventName() != null)
			.filter(subscription -> !INTERNAL_MESSAGES.contains(subscription.getEventName()))
			.toList();
	}

	private static List<AwaitingSignal> toSignals(final List<EventSubscriptionDto> gates, final ProcessModel model) {
		return gates.stream()
			.map(gate -> new AwaitingSignal(gate.getEventName(), model.labelOf(gate.getActivityId(), gate.getEventName())))
			.toList();
	}
}
