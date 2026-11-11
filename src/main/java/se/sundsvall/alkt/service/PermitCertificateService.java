package se.sundsvall.alkt.service;

import generated.se.sundsvall.messaging.DeliveryResult;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.messaging.MessageResult;
import generated.se.sundsvall.messaging.MessageStatus;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.messaging.MessageStatus.SENT;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.messaging.mapper.MessagingMapper.toDigitalMailRequest;
import static se.sundsvall.alkt.integration.messaging.mapper.MessagingMapper.toSnailmailRequest;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findPermitHolder;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseFileName;
import static se.sundsvall.alkt.util.FailureDescription.describe;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Service
public class PermitCertificateService {

	private static final Logger LOG = LoggerFactory.getLogger(PermitCertificateService.class);

	// Values: the channel, then the message id in messaging. PENDING from just before the send, and left in place when
	// the outcome is unknown.
	private static final String ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY = "permitCertificateDelivery";
	private static final List<String> PENDING_MARKER = List.of("PENDING");
	private static final int MARKER_WRITE_ATTEMPTS = 3;

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
		final var marker = findMarker(errand);
		if (marker.filter(PENDING_MARKER::equals).isPresent()) {
			throw new NonRetryableException("""
				The permit certificate of errand '%s' may already have been sent, as an earlier attempt did not learn whether it was. \
				Check in messaging whether it reached the permit holder. If it did, set errand parameter '%s' to the channel and \
				the message id, otherwise remove it to send the certificate again""".formatted(errandId, ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY));
		}
		final var alreadySent = marker.filter(values -> values.size() == 2)
			.map(values -> new CertificateDelivery(channelOf(errand, values.getFirst()), values.get(1), true));
		if (alreadySent.isPresent()) {
			return alreadySent.get();
		}
		final var letter = properties.letters().get(processType.getKey());
		final var permitHolder = findPermitHolder(errand)
			.orElseThrow(() -> new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId)));
		final var certificate = partyAssetsIntegration.findCertificate(municipalityId, assetId)
			.orElseThrow(() -> new NonRetryableException("Asset '%s' has no permit certificate to send".formatted(assetId)));
		final var fileName = toCaseFileName("Tillståndsbevis", errand);
		final var reachableMailbox = hasReachableMailbox(municipalityId, permitHolder);
		final var letterPossible = hasCompleteRecipient(permitHolder);
		if (!reachableMailbox && !letterPossible) {
			throw new NonRetryableException("The permit holder of errand '%s' has no reachable mailbox and no complete name and address to send the permit certificate to".formatted(errandId));
		}

		writeMarker(municipalityId, namespace, errandId, PENDING_MARKER);
		final var target = new Target(municipalityId, namespace, errandId);
		final Supplier<MessageResult> digitalMail = () -> sendDigitalMail(municipalityId, permitHolder, letter, fileName, certificate);
		final Supplier<MessageResult> snailMail = () -> sendSnailMail(municipalityId, permitHolder, fileName, certificate);
		if (!reachableMailbox) {
			return sendAndMark(target, Channel.SNAIL_MAIL, snailMail, true);
		}
		if (!letterPossible) {
			return sendAndMark(target, Channel.DIGITAL_MAIL, digitalMail, true);
		}
		final var digitalResult = tryDigitalMail(target, digitalMail);
		if (digitalResult.filter(PermitCertificateService::isDelivered).isPresent()) {
			return markSent(target, new CertificateDelivery(Channel.DIGITAL_MAIL, digitalResult.get().getMessageId().toString(), false));
		}
		return sendAndMark(target, Channel.SNAIL_MAIL, snailMail, digitalResult.isPresent());
	}

	/**
	 * Gives an undelivered result when messaging refused the digital mail, and nothing when its outcome is unknown. The
	 * letter goes either way.
	 */
	private static Optional<MessageResult> tryDigitalMail(final Target target, final Supplier<MessageResult> send) {
		try {
			return Optional.of(send.get());
		} catch (final ClientProblem | CallNotPermittedException e) {
			LOG.warn("The permit certificate of errand {} goes as a letter, as the digital mail was refused: {}", sanitizeForLogging(target.errandId()), describe(e));
			return Optional.of(new MessageResult());
		} catch (final RuntimeException e) {
			LOG.warn("The permit certificate of errand {} goes as a letter, as the outcome of the digital mail is unknown: {}", sanitizeForLogging(target.errandId()), describe(e));
			return Optional.empty();
		}
	}

	/**
	 * A result or a 4xx or an open circuit tells this send went nowhere. The marker is then removed for a retry, unless a
	 * digital mail before it may have gone out. Any other failure keeps the marker and stops on an incident.
	 */
	private CertificateDelivery sendAndMark(final Target target, final Channel channel, final Supplier<MessageResult> send, final boolean nothingSentBefore) {
		final MessageResult message;
		try {
			message = send.get();
		} catch (final ClientProblem | CallNotPermittedException e) {
			if (nothingSentBefore) {
				writeMarker(target.municipalityId(), target.namespace(), target.errandId(), emptyList());
				throw e;
			}
			throw digitalMailMayHaveBeenSent(target, describe(e), e);
		} catch (final RuntimeException e) {
			throw new NonRetryableException("The permit certificate of errand '%s' may have been sent, as the answer from messaging does not tell: %s".formatted(target.errandId(), describe(e)), e);
		}
		if (isDelivered(message)) {
			return markSent(target, new CertificateDelivery(channel, message.getMessageId().toString(), false));
		}
		final var notDelivered = "not delivered as %s, delivery statuses %s".formatted(channel, statusesOf(message));
		if (!nothingSentBefore) {
			throw digitalMailMayHaveBeenSent(target, notDelivered, null);
		}
		writeMarker(target.municipalityId(), target.namespace(), target.errandId(), emptyList());
		throw Problem.valueOf(BAD_GATEWAY, "The permit certificate of errand '%s' was %s".formatted(target.errandId(), notDelivered));
	}

	private static NonRetryableException digitalMailMayHaveBeenSent(final Target target, final String letterFailure, final Throwable cause) {
		return new NonRetryableException("The permit certificate of errand '%s' may have been sent as digital mail, whose outcome is unknown, and the letter after it failed: %s"
			.formatted(target.errandId(), letterFailure), cause);
	}

	private static Optional<List<String>> findMarker(final Errand errand) {
		return Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY.equals(parameter.getKey()))
			.map(Parameter::getValues)
			.filter(Objects::nonNull)
			.findFirst();
	}

	private static Channel channelOf(final Errand errand, final String value) {
		return Arrays.stream(Channel.values())
			.filter(channel -> channel.name().equals(value))
			.findFirst()
			.orElseThrow(() -> new NonRetryableException("Errand parameter '%s' of errand '%s' holds an unknown channel".formatted(ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY, errand.getId())));
	}

	private CertificateDelivery markSent(final Target target, final CertificateDelivery delivery) {
		try {
			writeMarker(target.municipalityId(), target.namespace(), target.errandId(), List.of(delivery.channel().name(), delivery.messageId()));
		} catch (final RuntimeException e) {
			throw new NonRetryableException("The permit certificate of errand '%s' was sent as %s with message id '%s' but could not be marked on the errand: %s"
				.formatted(target.errandId(), delivery.channel(), delivery.messageId(), describe(e)), e);
		}
		return delivery;
	}

	/**
	 * Written with the version of a fresh read, so a change saved meanwhile is kept. A 412 is retried here rather than
	 * by the task, which would send the certificate again. No values removes the marker.
	 */
	private void writeMarker(final String municipalityId, final String namespace, final String errandId, final List<String> values) {
		for (var attempt = 1;; attempt++) {
			final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
			final var parameters = Stream.concat(
				Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream().filter(parameter -> !ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY.equals(parameter.getKey())),
				Stream.of(values).filter(not(List::isEmpty)).map(markerValues -> new Parameter().key(ERRAND_PARAMETER_PERMIT_CERTIFICATE_DELIVERY).values(markerValues)))
				.toList();
			try {
				supportManagementIntegration.updateErrandParameters(municipalityId, namespace, errandId, errand.getVersion(), parameters);
				return;
			} catch (final ClientProblem e) {
				if (!PRECONDITION_FAILED.equals(e.getStatus()) || attempt == MARKER_WRITE_ATTEMPTS) {
					throw e;
				}
			}
		}
	}

	private static boolean isDelivered(final MessageResult message) {
		final var statuses = statusesOf(message);
		return !statuses.isEmpty() && statuses.stream().allMatch(SENT::equals);
	}

	private static List<MessageStatus> statusesOf(final MessageResult message) {
		return Optional.ofNullable(message.getDeliveries()).orElse(emptyList()).stream()
			.map(DeliveryResult::getStatus)
			.toList();
	}

	private static boolean hasCompleteRecipient(final Stakeholder permitHolder) {
		return (isNotBlank(permitHolder.getOrganizationName()) || (isNotBlank(permitHolder.getFirstName()) && isNotBlank(permitHolder.getLastName())))
			&& isNotBlank(permitHolder.getAddress()) && isNotBlank(permitHolder.getZipCode()) && isNotBlank(permitHolder.getCity());
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

	private record Target(String municipalityId, String namespace, String errandId) {}
}
