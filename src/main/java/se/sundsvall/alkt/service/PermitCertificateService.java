package se.sundsvall.alkt.service;

import generated.se.sundsvall.messaging.Address;
import generated.se.sundsvall.messaging.DeliveryResult;
import generated.se.sundsvall.messaging.DigitalMailAttachment;
import generated.se.sundsvall.messaging.DigitalMailParty;
import generated.se.sundsvall.messaging.DigitalMailRequest;
import generated.se.sundsvall.messaging.DigitalMailSender;
import generated.se.sundsvall.messaging.DigitalMailSenderSupportInfo;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.messaging.MessageResult;
import generated.se.sundsvall.messaging.SnailmailAttachment;
import generated.se.sundsvall.messaging.SnailmailParty;
import generated.se.sundsvall.messaging.SnailmailRequest;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
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
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.messaging.DigitalMailAttachment.ContentTypeEnum.APPLICATION_PDF;
import static generated.se.sundsvall.messaging.DigitalMailRequest.ContentTypeEnum.TEXT_PLAIN;
import static generated.se.sundsvall.messaging.MessageStatus.SENT;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findPermitHolder;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseFileName;

@Service
public class PermitCertificateService {

	// Values: the channel, then the message id in messaging.
	private static final String ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY = "permitCertificateDelivery";

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
		final var alreadySent = findDelivery(errand);
		if (alreadySent.isPresent()) {
			return alreadySent.get();
		}
		final var letter = Optional.ofNullable(properties.letters())
			.map(letters -> letters.get(processType.getKey()))
			.orElseThrow(() -> new NonRetryableException("No letter for the permit certificate is configured for process '%s'".formatted(processType.getKey())));
		final var permitHolder = findPermitHolder(errand)
			.orElseThrow(() -> new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId)));
		final var certificate = partyAssetsIntegration.findCertificate(municipalityId, assetId)
			.orElseThrow(() -> new NonRetryableException("Asset '%s' has no permit certificate to send".formatted(assetId)));
		final var fileName = toCaseFileName("Tillståndsbevis", errand);

		final CertificateDelivery delivery;
		if (hasReachableMailbox(municipalityId, permitHolder)) {
			delivery = new CertificateDelivery(Channel.DIGITAL_MAIL, requireDelivered(errandId, Channel.DIGITAL_MAIL, sendDigitalMail(municipalityId, permitHolder, letter, fileName, certificate)), false);
		} else {
			if (!hasCompleteAddress(permitHolder)) {
				throw new NonRetryableException("The permit holder of errand '%s' has no reachable mailbox and no complete address to send the permit certificate to".formatted(errandId));
			}
			delivery = new CertificateDelivery(Channel.SNAIL_MAIL, requireDelivered(errandId, Channel.SNAIL_MAIL, sendSnailMail(municipalityId, permitHolder, fileName, certificate)), false);
		}
		markSent(municipalityId, namespace, errandId, delivery);
		return delivery;
	}

	private static Optional<CertificateDelivery> findDelivery(final Errand errand) {
		return Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY.equals(parameter.getKey()))
			.map(Parameter::getValues)
			.filter(values -> values != null && values.size() == 2)
			.map(values -> new CertificateDelivery(Channel.valueOf(values.getFirst()), values.get(1), true))
			.findFirst();
	}

	/** Read again just before the write, so that a change made while the letter was sent is kept. */
	private void markSent(final String municipalityId, final String namespace, final String errandId, final CertificateDelivery delivery) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var parameters = Stream.concat(
			Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream().filter(parameter -> !ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY.equals(parameter.getKey())),
			Stream.of(new Parameter().key(ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY).values(List.of(delivery.channel().name(), delivery.messageId()))))
			.toList();
		supportManagementIntegration.updateErrandParameters(municipalityId, namespace, errandId, errand.getVersion(), parameters);
	}

	/** Delivered means every delivery of the message has status SENT. */
	private static String requireDelivered(final String errandId, final Channel channel, final MessageResult message) {
		final var statuses = Optional.ofNullable(message).map(MessageResult::getDeliveries).orElse(emptyList()).stream()
			.map(DeliveryResult::getStatus)
			.toList();
		if (statuses.isEmpty() || !statuses.stream().allMatch(SENT::equals)) {
			throw Problem.valueOf(BAD_GATEWAY, "The permit certificate of errand '%s' was not delivered as %s, delivery statuses %s".formatted(errandId, channel, statuses));
		}
		return message.getMessageId().toString();
	}

	private static boolean hasCompleteAddress(final Stakeholder permitHolder) {
		return isNotBlank(permitHolder.getAddress()) && isNotBlank(permitHolder.getZipCode()) && isNotBlank(permitHolder.getCity());
	}

	/** An organization is addressed by its name, a person by first and last name. */
	private MessageResult sendSnailMail(final String municipalityId, final Stakeholder permitHolder, final String fileName, final byte[] certificate) {
		final var address = new Address()
			.careOf(permitHolder.getCareOf())
			.address(permitHolder.getAddress())
			.zipCode(permitHolder.getZipCode())
			.city(permitHolder.getCity())
			.country(permitHolder.getCountry());
		if (isNotBlank(permitHolder.getOrganizationName())) {
			address.organizationName(permitHolder.getOrganizationName());
		} else {
			address.firstName(permitHolder.getFirstName()).lastName(permitHolder.getLastName());
		}
		return messagingIntegration.sendSnailMail(municipalityId, new SnailmailRequest()
			.party(new SnailmailParty().partyId(permitHolder.getExternalId()))
			.address(address)
			.department(properties.department())
			.attachments(List.of(new SnailmailAttachment()
				.filename(fileName)
				.contentType(APPLICATION_PDF_VALUE)
				.content(Base64.getEncoder().encodeToString(certificate)))));
	}

	private boolean hasReachableMailbox(final String municipalityId, final Stakeholder permitHolder) {
		return messagingIntegration.getMailboxes(municipalityId, properties.organizationNumber(), permitHolder.getExternalId()).stream()
			.map(Mailbox::getReachable)
			.anyMatch(TRUE::equals);
	}

	private MessageResult sendDigitalMail(final String municipalityId, final Stakeholder permitHolder, final Letter letter, final String fileName, final byte[] certificate) {
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
		return Optional.ofNullable(result.getMessages()).orElse(emptyList()).stream().findFirst().orElse(null);
	}
}
