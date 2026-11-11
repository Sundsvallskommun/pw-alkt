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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.Letter;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.SupportInfo;

import static generated.se.sundsvall.messaging.DigitalMailAttachment.ContentTypeEnum.APPLICATION_PDF;
import static generated.se.sundsvall.messaging.DigitalMailRequest.ContentTypeEnum.TEXT_PLAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.messaging.mapper.MessagingMapper.toDigitalMailRequest;
import static se.sundsvall.alkt.integration.messaging.mapper.MessagingMapper.toSnailmailRequest;

class MessagingMapperTest {

	private static final String PARTY_ID = "f427952b-247c-4d3b-b081-675a467b3619";
	private static final String DEPARTMENT = "SBK(Alkohol och tobak)";
	private static final String FILE_NAME = "Tillståndsbevis ALKT-2026-000123.pdf";
	private static final byte[] CERTIFICATE = "%PDF-1.7 tillståndsbevis".getBytes(StandardCharsets.UTF_8);

	@Test
	void toDigitalMailRequestCarriesTheLetterTheSupportInfoAndTheCertificate() {
		final var permitHolder = new Stakeholder().externalId(PARTY_ID).organizationName("Restaurang Norrsken AB");
		final var supportInfo = new SupportInfo("Kontakta alkohol- och tobaksenheten", "alkohol.tobak@sundsvall.se", "060-19 10 00", "https://sundsvall.se/alkohol");
		final var letter = new Letter("Ditt serveringstillstånd", "Här är beviset på ditt serveringstillstånd.");

		final var result = toDigitalMailRequest(permitHolder, supportInfo, DEPARTMENT, letter, FILE_NAME, CERTIFICATE);

		assertThat(result).isEqualTo(new DigitalMailRequest()
			.party(new DigitalMailParty().partyIds(List.of(UUID.fromString(PARTY_ID))))
			.sender(new DigitalMailSender().supportInfo(new DigitalMailSenderSupportInfo()
				.text("Kontakta alkohol- och tobaksenheten")
				.emailAddress("alkohol.tobak@sundsvall.se")
				.phoneNumber("060-19 10 00")
				.url("https://sundsvall.se/alkohol")))
			.subject("Ditt serveringstillstånd")
			.department(DEPARTMENT)
			.contentType(TEXT_PLAIN)
			.body("Här är beviset på ditt serveringstillstånd.")
			.attachments(List.of(new DigitalMailAttachment()
				.contentType(APPLICATION_PDF)
				.content(Base64.getEncoder().encodeToString(CERTIFICATE))
				.filename(FILE_NAME))));
	}

	@Test
	void toSnailmailRequestToACompanyCarriesTheOrganizationNameAndTheAddress() {
		final var permitHolder = new Stakeholder()
			.externalId(PARTY_ID)
			.organizationName("Restaurang Norrsken AB")
			.firstName("Anna")
			.lastName("Svensson")
			.careOf("Anna Svensson")
			.address("Storgatan 12")
			.zipCode("852 30")
			.city("Sundsvall")
			.country("Sverige");

		final var result = toSnailmailRequest(permitHolder, DEPARTMENT, FILE_NAME, CERTIFICATE);

		assertThat(result).isEqualTo(new SnailmailRequest()
			.party(new SnailmailParty().partyId(PARTY_ID))
			.address(new Address()
				.organizationName("Restaurang Norrsken AB")
				.careOf("Anna Svensson")
				.address("Storgatan 12")
				.zipCode("852 30")
				.city("Sundsvall")
				.country("Sverige"))
			.department(DEPARTMENT)
			.attachments(List.of(new SnailmailAttachment()
				.filename(FILE_NAME)
				.contentType("application/pdf")
				.content(Base64.getEncoder().encodeToString(CERTIFICATE)))));
	}

	@Test
	void toSnailmailRequestToAPersonCarriesTheFirstAndLastName() {
		final var permitHolder = new Stakeholder()
			.externalId(PARTY_ID)
			.firstName("Erik")
			.lastName("Lindqvist")
			.address("Storgatan 12")
			.zipCode("852 30")
			.city("Sundsvall");

		final var result = toSnailmailRequest(permitHolder, DEPARTMENT, FILE_NAME, CERTIFICATE);

		assertThat(result.getAddress()).isEqualTo(new Address()
			.firstName("Erik")
			.lastName("Lindqvist")
			.address("Storgatan 12")
			.zipCode("852 30")
			.city("Sundsvall"));
	}
}
