package se.sundsvall.alkt.service;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static se.sundsvall.alkt.Constants.MESSAGE_ERRAND_UPDATED;
import static se.sundsvall.alkt.Constants.PROCESS_KEYS;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Service
public class ProcessService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessService.class);

	private static final String SUB_TYPE_SIGNAL = "SIGNAL";

	private final OperatonIntegration operatonIntegration;
	private final ProcessReportService processReportService;

	ProcessService(final OperatonIntegration operatonIntegration, final ProcessReportService processReportService) {
		this.operatonIntegration = operatonIntegration;
		this.processReportService = processReportService;
	}

	public void handleErrandEvent(final String municipalityId, final String namespace, final ErrandEvent errandEvent) {
		if (errandEvent.getEventType() == ErrandEvent.EventType.DELETE) {
			deleteProcess(errandEvent);
			return;
		}

		if (operatonIntegration.findProcessInstances(errandEvent.getErrandId(), errandEvent.getProcessKey(), TENANT_ID_ALKT).isEmpty()) {
			startProcess(municipalityId, namespace, errandEvent);
		} else {
			correlateMessage(municipalityId, namespace, errandEvent);
		}
	}

	private void deleteProcess(final ErrandEvent errandEvent) {
		operatonIntegration.findProcessInstances(errandEvent.getErrandId(), null, TENANT_ID_ALKT)
			.forEach(processInstance -> {
				LOG.info("Deleting process instance {} of deleted errand {}", sanitizeForLogging(processInstance.getId()), sanitizeForLogging(errandEvent.getErrandId()));
				operatonIntegration.deleteProcessInstance(processInstance.getId());
			});
	}

	private void startProcess(final String municipalityId, final String namespace, final ErrandEvent errandEvent) {
		if (isBlank(errandEvent.getProcessKey())) {
			LOG.info("Errand {} carries no process key, so there is no process to start", sanitizeForLogging(errandEvent.getErrandId()));
			return;
		}

		if (!errandEvent.isStartAllowed()) {
			LOG.info("Event {} on errand {} is not allowed to start a process", sanitizeForLogging(errandEvent.getEventId()), sanitizeForLogging(errandEvent.getErrandId()));
			return;
		}

		if (!PROCESS_KEYS.contains(errandEvent.getProcessKey())) {
			throw Problem.valueOf(UNPROCESSABLE_CONTENT, "Process key '%s' matches no deployed process definition".formatted(errandEvent.getProcessKey()));
		}

		final var instance = operatonIntegration.startProcess(municipalityId, namespace, errandEvent.getErrandId(), errandEvent.getProcessKey(), TENANT_ID_ALKT);

		LOG.info("Started process {} as instance {} for errand {}", sanitizeForLogging(errandEvent.getProcessKey()), sanitizeForLogging(instance.getId()),
			sanitizeForLogging(errandEvent.getErrandId()));

		processReportService.reportWaitState(
			new ReportTarget(municipalityId, namespace, errandEvent.getErrandId(), instance.getId(), errandEvent.getProcessKey(), null), instance.getDefinitionId());
	}

	private void correlateMessage(final String municipalityId, final String namespace, final ErrandEvent errandEvent) {
		final var messageName = SUB_TYPE_SIGNAL.equalsIgnoreCase(errandEvent.getEventSubType()) ? errandEvent.getSignalName() : MESSAGE_ERRAND_UPDATED;

		if (isBlank(messageName)) {
			LOG.error("Event {} on errand {} is a signal without a name, so there is no gate to open", sanitizeForLogging(errandEvent.getEventId()),
				sanitizeForLogging(errandEvent.getErrandId()));
			return;
		}

		final Optional<String> reached;
		try {
			reached = operatonIntegration.correlateMessage(messageName, errandEvent.getErrandId(), TENANT_ID_ALKT);
			LOG.info("Correlated '{}' for errand {}", sanitizeForLogging(messageName), sanitizeForLogging(errandEvent.getErrandId()));
		} catch (final ClientProblem e) {
			// The process stands where it stood, so its wait state is the one Support Management already knows about.
			LOG.info("Message '{}' correlated to no running wait state of errand {}: {}", sanitizeForLogging(messageName), sanitizeForLogging(errandEvent.getErrandId()),
				sanitizeForLogging(e.getMessage()));
			return;
		}

		// Looked up rather than taken from the event: the correlation picks on the subscription, so the instance it reached
		// is not always one the process key of the event names.
		reached.flatMap(operatonIntegration::findProcessInstance)
			.ifPresentOrElse(
				instance -> processReportService.reportWaitState(
					new ReportTarget(municipalityId, namespace, errandEvent.getErrandId(), instance.getId(), instance.getDefinitionKey(), null), instance.getDefinitionId()),
				() -> LOG.info("Message '{}' ran the process of errand {} to its end, so there is no wait state left to report",
					sanitizeForLogging(messageName), sanitizeForLogging(errandEvent.getErrandId())));
	}
}
