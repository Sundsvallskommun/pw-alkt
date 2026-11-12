package se.sundsvall.alkt.businesslogic.worker;

import java.time.Duration;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.configuration.ReconciliationProperties;
import se.sundsvall.alkt.service.ProcessReconciliationService;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.alkt.Constants.PROCESS_KEY_RECONCILIATION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_RECONCILIATION_NIGHTLY;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/**
 * The work step of both reconciliation models, the scheduler of pw-alkt: the engine fires their timers once per cycle
 * and locks the task for one pod. No errand, so not an AbstractTaskWorker.
 */
@Component
@ConditionalOnProperty(name = "reconciliation.worker.enabled", matchIfMissing = true)
@ExternalTaskSubscription(topicName = "ReconcileProcessesTask", lockDuration = ReconcileProcessesWorker.LOCK_DURATION_IN_MILLISECONDS)
public class ReconcileProcessesWorker implements ExternalTaskHandler {

	// Generous on purpose: a lock that expires mid-sweep hands the task to the other pod, which then sweeps in parallel.
	// The only cost of a long lock is that a sweep of a crashed pod is redone later.
	static final long LOCK_DURATION_IN_MILLISECONDS = 30 * 60 * 1000L;

	private static final Logger LOG = LoggerFactory.getLogger(ReconcileProcessesWorker.class);

	private final ProcessReconciliationService processReconciliationService;
	private final ReconciliationProperties properties;

	ReconcileProcessesWorker(final ProcessReconciliationService processReconciliationService, final ReconciliationProperties properties) {
		this.processReconciliationService = processReconciliationService;
		this.properties = properties;
	}

	@Override
	public void execute(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		RequestId.init();
		try {
			processReconciliationService.reconcile(externalTask.getProcessDefinitionKey(), lookbackOf(externalTask));
			externalTaskService.complete(externalTask);
		} finally {
			RequestId.reset();
		}
	}

	private Duration lookbackOf(final ExternalTask externalTask) {
		final var processKey = externalTask.getProcessDefinitionKey();
		final var lookback = switch (processKey) {
			case PROCESS_KEY_RECONCILIATION -> properties.lookback();
			case PROCESS_KEY_RECONCILIATION_NIGHTLY -> properties.nightlyLookback();
			case null, default -> {
				LOG.warn("Task {} belongs to unknown reconciliation process {}, sweeps the window of {}", sanitizeForLogging(externalTask.getId()), sanitizeForLogging(processKey), PROCESS_KEY_RECONCILIATION);
				yield properties.lookback();
			}
		};
		LOG.debug("Reconciliation {} sweeps the last {}", sanitizeForLogging(processKey), lookback);
		return lookback;
	}
}
