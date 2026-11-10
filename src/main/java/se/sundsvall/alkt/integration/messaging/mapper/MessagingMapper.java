package se.sundsvall.alkt.integration.messaging.mapper;

import generated.se.sundsvall.messaging.Address;
import generated.se.sundsvall.messaging.DigitalMailAttachment;
import generated.se.sundsvall.messaging.DigitalMailParty;
import generated.se.sundsvall.messaging.DigitalMailRequest;
import generated.se.sundsvall.messaging.DigitalMailSender;
import generated.se.sundsvall.messaging.DigitalMailSenderSupportInfo;
import generated.se.sundsvall.messaging.SnailmailAttachment;
import generated.se.sundsvall.messaging.SnailmailParty;
import generated.se.sundsvall.messaging.SnailmailRequest;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.Letter;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.SupportInfo;

import static generated.se.sundsvall.messaging.DigitalMailAttachment.ContentTypeEnum.APPLICATION_PDF;
import static generated.se.sundsvall.messaging.DigitalMailRequest.ContentTypeEnum.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;

public final class MessagingMapper {

	private MessagingMapper() {}

	public static SnailmailRequest toSnailmailRequest(final Stakeholder permitHolder, final String department, final String fileName, final byte[] certificate) {
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
		return new SnailmailRequest()
			.party(new SnailmailParty().partyId(permitHolder.getExternalId()))
			.address(address)
			.department(department)
			.attachments(List.of(new SnailmailAttachment()
				.filename(fileName)
				.contentType(APPLICATION_PDF_VALUE)
				.content(Base64.getEncoder().encodeToString(certificate))));
	}

	public static DigitalMailRequest toDigitalMailRequest(final Stakeholder permitHolder, final SupportInfo supportInfo, final String department, final Letter letter, final String fileName,
		final byte[] certificate) {
		return new DigitalMailRequest()
			.party(new DigitalMailParty().partyIds(List.of(UUID.fromString(permitHolder.getExternalId()))))
			.sender(new DigitalMailSender().supportInfo(new DigitalMailSenderSupportInfo()
				.text(supportInfo.text())
				.emailAddress(supportInfo.emailAddress())
				.phoneNumber(supportInfo.phoneNumber())
				.url(supportInfo.url())))
			.subject(letter.subject())
			.department(department)
			.contentType(TEXT_PLAIN)
			.body(letter.body())
			.attachments(List.of(new DigitalMailAttachment()
				.contentType(APPLICATION_PDF)
				.content(Base64.getEncoder().encodeToString(certificate))
				.filename(fileName)));
	}
}
