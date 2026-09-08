package se.sundsvall.alkt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.alkt.integration.operaton.mapper.OperatonMapper;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static se.sundsvall.alkt.Constants.PROCESS_KEYS;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Service
public class ProcessService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessService.class);

	private static final String MESSAGE_ERRAND_UPDATED = "errandUpdated";
	private static final String SUB_TYPE_SIGNAL = "SIGNAL";

	private final OperatonClient operatonClient;

	ProcessService(OperatonClient operatonClient) {
		this.operatonClient = operatonClient;
	}

	public void handleErrandEvent(final String municipalityId, final String namespace, final ErrandEvent errandEvent) {
		if (errandEvent.getEventType() == ErrandEvent.EventType.DELETE) {
			deleteProcess(errandEvent);
			return;
		}

		if (operatonClient.findProcessInstances(errandEvent.getErrandId(), errandEvent.getProcessKey(), TENANT_ID_ALKT).isEmpty()) {
			startProcess(municipalityId, namespace, errandEvent);
		} else {
			correlateMessage(errandEvent);
		}
	}

	private void deleteProcess(final ErrandEvent errandEvent) {
		operatonClient.findProcessInstances(errandEvent.getErrandId(), null, TENANT_ID_ALKT)
			.forEach(processInstance -> {
				LOG.info("Deleting process instance {} of deleted errand {}", sanitizeForLogging(processInstance.getId()), sanitizeForLogging(errandEvent.getErrandId()));
				operatonClient.deleteProcessInstance(processInstance.getId(), false);
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

		final var processInstance = operatonClient.startProcessWithTenant(errandEvent.getProcessKey(), TENANT_ID_ALKT,
			OperatonMapper.toStartProcessInstanceDto(municipalityId, namespace, errandEvent.getErrandId()));

		LOG.info("Started process {} as instance {} for errand {}", sanitizeForLogging(errandEvent.getProcessKey()), sanitizeForLogging(processInstance.getId()),
			sanitizeForLogging(errandEvent.getErrandId()));
	}

	private void correlateMessage(final ErrandEvent errandEvent) {
		final var messageName = SUB_TYPE_SIGNAL.equalsIgnoreCase(errandEvent.getEventSubType()) ? errandEvent.getSignalName() : MESSAGE_ERRAND_UPDATED;

		if (isBlank(messageName)) {
			LOG.error("Event {} on errand {} is a signal without a name, so there is no gate to open", sanitizeForLogging(errandEvent.getEventId()),
				sanitizeForLogging(errandEvent.getErrandId()));
			return;
		}

		try {
			operatonClient.correlateMessage(OperatonMapper.toCorrelationMessageDto(messageName, errandEvent.getErrandId(), TENANT_ID_ALKT));
			LOG.info("Correlated '{}' for errand {}", sanitizeForLogging(messageName), sanitizeForLogging(errandEvent.getErrandId()));
		} catch (final ClientProblem e) {
			LOG.info("Message '{}' correlated to no running wait state of errand {}: {}", sanitizeForLogging(messageName), sanitizeForLogging(errandEvent.getErrandId()),
				sanitizeForLogging(e.getMessage()));
		}
	}
}
