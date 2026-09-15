package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.service.ProcessReconciliationService;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/**
 * The work step of process-reconciliation.bpmn, the scheduler of pw-alkt: the engine fires its timer once per cycle and
 * locks the task for one pod. No errand, so not an AbstractTaskWorker. See README, "Process reconciliation".
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

	ReconcileProcessesWorker(final ProcessReconciliationService processReconciliationService) {
		this.processReconciliationService = processReconciliationService;
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
			processReconciliationService.reconcile();
		} catch (final Exception e) {
			LOG.error("Reconciliation failed in task {}, the next cycle is the retry", sanitizeForLogging(externalTask.getId()), e);
		}
	}
}
