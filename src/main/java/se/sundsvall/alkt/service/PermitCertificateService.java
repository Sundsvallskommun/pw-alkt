package se.sundsvall.alkt.service;

import generated.se.sundsvall.messaging.DeliveryResult;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.messaging.MessageResult;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

import static generated.se.sundsvall.messaging.MessageStatus.SENT;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.messaging.mapper.MessagingMapper.toDigitalMailRequest;
import static se.sundsvall.alkt.integration.messaging.mapper.MessagingMapper.toSnailmailRequest;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findPermitHolder;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseFileName;
import static se.sundsvall.alkt.util.FailureDescription.describe;

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
		final var letter = properties.letters().get(processType.getKey());
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
		try {
			markSent(municipalityId, namespace, errandId, delivery);
		} catch (final RuntimeException e) {
			throw new NonRetryableException("The permit certificate of errand '%s' was sent as %s with message id '%s' but could not be marked on the errand: %s"
				.formatted(errandId, delivery.channel(), delivery.messageId(), describe(e)), e);
		}
		return delivery;
	}

	private static Optional<CertificateDelivery> findDelivery(final Errand errand) {
		return Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY.equals(parameter.getKey()))
			.map(Parameter::getValues)
			.filter(values -> values != null && values.size() == 2)
			.map(values -> new CertificateDelivery(channelOf(errand, values.getFirst()), values.get(1), true))
			.findFirst();
	}

	private static Channel channelOf(final Errand errand, final String value) {
		return Arrays.stream(Channel.values())
			.filter(channel -> channel.name().equals(value))
			.findFirst()
			.orElseThrow(() -> new NonRetryableException("Errand parameter '%s' of errand '%s' holds an unknown channel".formatted(ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY, errand.getId())));
	}

	/**
	 * Read again just before the write, so that a change made while the letter was sent is kept. Written without If-Match,
	 * as a 412 would retry the task and send the letter again.
	 */
	private void markSent(final String municipalityId, final String namespace, final String errandId, final CertificateDelivery delivery) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var parameters = Stream.concat(
			Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream().filter(parameter -> !ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY.equals(parameter.getKey())),
			Stream.of(new Parameter().key(ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY).values(List.of(delivery.channel().name(), delivery.messageId()))))
			.toList();
		supportManagementIntegration.updateErrandParameters(municipalityId, namespace, errandId, null, parameters);
	}

	private static String requireDelivered(final String errandId, final Channel channel, final MessageResult message) {
		final var statuses = Optional.ofNullable(message.getDeliveries()).orElse(emptyList()).stream()
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

	private MessageResult sendSnailMail(final String municipalityId, final Stakeholder permitHolder, final String fileName, final byte[] certificate) {
		return messagingIntegration.sendSnailMail(municipalityId, toSnailmailRequest(permitHolder, properties.department(), fileName, certificate));
	}

	private boolean hasReachableMailbox(final String municipalityId, final Stakeholder permitHolder) {
		return messagingIntegration.getMailboxes(municipalityId, properties.organizationNumber(), permitHolder.getExternalId()).stream()
			.map(Mailbox::getReachable)
			.anyMatch(TRUE::equals);
	}

	private MessageResult sendDigitalMail(final String municipalityId, final Stakeholder permitHolder, final Letter letter, final String fileName, final byte[] certificate) {
		final var result = messagingIntegration.sendDigitalMail(municipalityId, properties.organizationNumber(),
			toDigitalMailRequest(permitHolder, properties.supportInfo(), properties.department(), letter, fileName, certificate));
		return Optional.ofNullable(result.getMessages()).orElse(emptyList()).stream().findFirst().orElseGet(MessageResult::new);
	}
}
