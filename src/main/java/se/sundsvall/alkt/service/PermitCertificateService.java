package se.sundsvall.alkt.service;

import generated.se.sundsvall.messaging.DigitalMailAttachment;
import generated.se.sundsvall.messaging.DigitalMailParty;
import generated.se.sundsvall.messaging.DigitalMailRequest;
import generated.se.sundsvall.messaging.DigitalMailSender;
import generated.se.sundsvall.messaging.DigitalMailSenderSupportInfo;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.configuration.PermitCertificateProperties;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.Letter;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.messaging.MessagingIntegration;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.CertificateDelivery;
import se.sundsvall.alkt.service.model.CertificateDelivery.Channel;
import se.sundsvall.alkt.service.model.ProcessType;

import static generated.se.sundsvall.messaging.DigitalMailAttachment.ContentTypeEnum.APPLICATION_PDF;
import static generated.se.sundsvall.messaging.DigitalMailRequest.ContentTypeEnum.TEXT_PLAIN;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findPermitHolder;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseFileName;

@Service
public class PermitCertificateService {

	private final SupportManagementIntegration supportManagementIntegration;
	private final PartyAssetsIntegration partyAssetsIntegration;
	private final MessagingIntegration messagingIntegration;
	private final PermitCertificateProperties properties;

	PermitCertificateService(final SupportManagementIntegration supportManagementIntegration, final PartyAssetsIntegration partyAssetsIntegration,
		final MessagingIntegration messagingIntegration, final PermitCertificateProperties properties) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.partyAssetsIntegration = partyAssetsIntegration;
		this.messagingIntegration = messagingIntegration;
		this.properties = properties;
	}

	public CertificateDelivery sendCertificate(final String municipalityId, final String namespace, final String errandId, final ProcessType processType, final String assetId) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var letter = Optional.ofNullable(properties.letters())
			.map(letters -> letters.get(processType.getKey()))
			.filter(found -> isNotBlank(found.subject()) && isNotBlank(found.body()))
			.orElseThrow(() -> new NonRetryableException("No letter for the permit certificate is configured for process '%s'".formatted(processType.getKey())));
		final var permitHolder = findPermitHolder(errand)
			.orElseThrow(() -> new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId)));
		final var certificate = partyAssetsIntegration.findCertificate(municipalityId, assetId).orElseThrow();
		final var fileName = toCaseFileName("Tillståndsbevis", errand);

		if (!hasReachableMailbox(municipalityId, permitHolder)) {
			throw new UnsupportedOperationException();
		}
		final var messageId = sendDigitalMail(municipalityId, permitHolder, letter, fileName, certificate);
		return new CertificateDelivery(Channel.DIGITAL_MAIL, messageId, false);
	}

	private boolean hasReachableMailbox(final String municipalityId, final Stakeholder permitHolder) {
		return messagingIntegration.getMailboxes(municipalityId, properties.organizationNumber(), permitHolder.getExternalId()).stream()
			.map(Mailbox::getReachable)
			.anyMatch(TRUE::equals);
	}

	private String sendDigitalMail(final String municipalityId, final Stakeholder permitHolder, final Letter letter, final String fileName, final byte[] certificate) {
		final var supportInfo = properties.supportInfo();
		final var result = messagingIntegration.sendDigitalMail(municipalityId, properties.organizationNumber(), new DigitalMailRequest()
			.party(new DigitalMailParty().partyIds(List.of(UUID.fromString(permitHolder.getExternalId()))))
			.sender(new DigitalMailSender().supportInfo(new DigitalMailSenderSupportInfo()
				.text(supportInfo.text())
				.emailAddress(supportInfo.emailAddress())
				.phoneNumber(supportInfo.phoneNumber())
				.url(supportInfo.url())))
			.subject(letter.subject())
			.department(properties.department())
			.contentType(TEXT_PLAIN)
			.body(letter.body())
			.attachments(List.of(new DigitalMailAttachment()
				.contentType(APPLICATION_PDF)
				.content(Base64.getEncoder().encodeToString(certificate))
				.filename(fileName))));
		return result.getMessages().getFirst().getMessageId().toString();
	}
}
