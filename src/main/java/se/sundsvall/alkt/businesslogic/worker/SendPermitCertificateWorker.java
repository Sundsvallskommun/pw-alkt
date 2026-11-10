package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.PermitCertificateService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.CertificateDelivery.Channel;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ASSET_ID;
import static se.sundsvall.alkt.service.model.CertificateDelivery.Channel.DIGITAL_MAIL;
import static se.sundsvall.alkt.service.model.CertificateDelivery.Channel.SNAIL_MAIL;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "SendPermitCertificateTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class SendPermitCertificateWorker extends AbstractTaskWorker {

	// The keys of the done texts of the step under process-log.
	private static final Map<Channel, String> DONE_VARIANTS = Map.of(DIGITAL_MAIL, "digital-mail", SNAIL_MAIL, "snail-mail");

	private final PermitCertificateService permitCertificateService;

	SendPermitCertificateWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final PermitCertificateService permitCertificateService) {
		super(processReportService, failureHandler);
		this.permitCertificateService = permitCertificateService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var delivery = permitCertificateService.sendCertificate(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			getProcessType(externalTask), externalTask.getVariable(PROCESS_VARIABLE_ASSET_ID));
		final var report = running(externalTask).withDoneVariant(DONE_VARIANTS.get(delivery.channel()));

		if (delivery.alreadySent()) {
			logInfo("Errand {} already had its permit certificate sent as {}", sanitizeForLogging(getErrandId(externalTask)), delivery.channel());
			return report.withLogMessage("Permit certificate already sent as %s, message '%s', not sent again".formatted(delivery.channel(), delivery.messageId()));
		}
		logInfo("Errand {} had its permit certificate sent as {}", sanitizeForLogging(getErrandId(externalTask)), delivery.channel());
		return report.withLogMessage("Permit certificate sent as %s, message '%s'".formatted(delivery.channel(), delivery.messageId()));
	}
}
