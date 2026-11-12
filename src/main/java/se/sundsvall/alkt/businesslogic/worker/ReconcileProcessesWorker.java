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

	public static final String PROCESS_KEY_RECONCILIATION_NIGHTLY = "process-reconciliation-nightly";

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
			sweep(externalTask);
			externalTaskService.complete(externalTask);
		} finally {
			RequestId.reset();
		}
	}

	private void sweep(final ExternalTask externalTask) {
		try {
			processReconciliationService.reconcile(lookbackOf(externalTask));
		} catch (final Exception e) {
			LOG.error("Reconciliation failed in task {}, the next run of its timer is the retry", sanitizeForLogging(externalTask.getId()), e);
		}
	}

	private Duration lookbackOf(final ExternalTask externalTask) {
		if (PROCESS_KEY_RECONCILIATION_NIGHTLY.equals(externalTask.getProcessDefinitionKey())) {
			return properties.nightlyLookback();
		}
		return properties.lookback();
	}
}
