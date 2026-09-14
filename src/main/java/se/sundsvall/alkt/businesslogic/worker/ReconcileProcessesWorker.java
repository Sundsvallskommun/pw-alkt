package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.service.ProcessReconciliationService;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/**
 * The work step of process-reconciliation.bpmn, which is the scheduler of pw-alkt: the engine fires its timer once per
 * cycle no matter how many pods poll, and locks the task for one of them. Not an AbstractTaskWorker, since there is no
 * errand to report to. A failed run is turned into an incident right away; the next cycle is a new attempt anyway.
 * See README, "Process reconciliation".
 */
@Component
@ExternalTaskSubscription(topicName = "ReconcileProcessesTask", lockDuration = ReconcileProcessesWorker.LOCK_DURATION_IN_MILLISECONDS)
public class ReconcileProcessesWorker implements ExternalTaskHandler {

	// Must hold a whole sweep and stay below the timer cycle of the model
	static final long LOCK_DURATION_IN_MILLISECONDS = 240_000;

	private static final Logger LOG = LoggerFactory.getLogger(ReconcileProcessesWorker.class);

	private final ProcessReconciliationService processReconciliationService;

	ReconcileProcessesWorker(final ProcessReconciliationService processReconciliationService) {
		this.processReconciliationService = processReconciliationService;
	}

	@Override
	public void execute(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		RequestId.init();
		try {
			processReconciliationService.reconcile();
			externalTaskService.complete(externalTask);
		} catch (final Exception e) {
			LOG.error("Reconciliation failed in task {}", sanitizeForLogging(externalTask.getId()), e);
			externalTaskService.handleFailure(externalTask.getId(), e.getMessage(), null, 0, 0);
		} finally {
			RequestId.reset();
		}
	}
}
