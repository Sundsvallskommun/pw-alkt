package se.sundsvall.alkt.service;

import generated.se.sundsvall.messaging.Address;
import generated.se.sundsvall.messaging.DeliveryResult;
import generated.se.sundsvall.messaging.DigitalMailAttachment;
import generated.se.sundsvall.messaging.DigitalMailParty;
import generated.se.sundsvall.messaging.DigitalMailRequest;
import generated.se.sundsvall.messaging.DigitalMailSender;
import generated.se.sundsvall.messaging.DigitalMailSenderSupportInfo;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.messaging.MessageBatchResult;
import generated.se.sundsvall.messaging.MessageResult;
import generated.se.sundsvall.messaging.SnailmailAttachment;
import generated.se.sundsvall.messaging.SnailmailParty;
import generated.se.sundsvall.messaging.SnailmailRequest;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.configuration.PermitCertificateProperties;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.Letter;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.SupportInfo;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.messaging.MessagingIntegration;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.CertificateDelivery;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static generated.se.sundsvall.messaging.DigitalMailAttachment.ContentTypeEnum.APPLICATION_PDF;
import static generated.se.sundsvall.messaging.DigitalMailRequest.ContentTypeEnum.TEXT_PLAIN;
import static generated.se.sundsvall.messaging.MessageStatus.FAILED;
import static generated.se.sundsvall.messaging.MessageStatus.NOT_SENT;
import static generated.se.sundsvall.messaging.MessageStatus.SENT;
import static generated.se.sundsvall.messaging.MessageType.DIGITAL_MAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING;
import static se.sundsvall.alkt.service.model.ProcessType.TOBACCO_SALES;

@ExtendWith(MockitoExtension.class)
class PermitCertificateServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "7a9c2b54-3f0e-4c1a-9d8e-2b6f1a0c4e11";
	private static final String ASSET_ID = "c1f2e3d4-5b6a-4789-8abc-def012345678";
	private static final String PARTY_ID = "f427952b-247c-4d3b-b081-675a467b3619";
	private static final String SUNDSVALL_ORGANIZATION_NUMBER = "2120002411";
	private static final UUID MESSAGE_ID = UUID.fromString("0b6c1d2e-8f3a-4b5c-9d7e-1a2b3c4d5e6f");
	private static final byte[] CERTIFICATE = "%PDF-1.7 tillståndsbevis".getBytes(StandardCharsets.UTF_8);

	private static final PermitCertificateProperties PROPERTIES = new PermitCertificateProperties(SUNDSVALL_ORGANIZATION_NUMBER, "SBK(Alkohol och tobak)",
		new SupportInfo("Kontakta alkohol- och tobaksenheten", "alkohol.tobak@sundsvall.se", "060-19 10 00", "https://sundsvall.se/alkohol"),
		Map.of(ALCOHOL_SERVING.getKey(), new Letter("Ditt serveringstillstånd", "Här är beviset på ditt serveringstillstånd.")));

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private PartyAssetsIntegration partyAssetsIntegrationMock;

	@Mock
	private MessagingIntegration messagingIntegrationMock;

	@Captor
	private ArgumentCaptor<SnailmailRequest> snailMailCaptor;

	private PermitCertificateService service;

	@BeforeEach
	void setUp() {
		service = new PermitCertificateService(supportManagementIntegrationMock, partyAssetsIntegrationMock, messagingIntegrationMock, PROPERTIES);
	}

	private static Errand errandOfCompany() {
		return new Errand()
			.id(ERRAND_ID)
			.errandNumber("ALKT-2026-000123")
			.version(4L)
			.parameters(List.of(new Parameter().key("premisesMunicipalityId").values(List.of("2281"))))
			.stakeholders(List.of(new Stakeholder()
				.role("PRIMARY")
				.externalId(PARTY_ID)
				.organizationName("Restaurang Norrsken AB")
				.careOf("Anna Svensson")
				.address("Storgatan 12")
				.zipCode("852 30")
				.city("Sundsvall")
				.country("Sverige")));
	}

	@Test
	void sendsTheCertificateAsDigitalMailWhenTheMailboxIsReachable() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).supplier("Kivra").reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(sentDigitalMail());

		service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		verify(messagingIntegrationMock).sendDigitalMail(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, new DigitalMailRequest()
			.party(new DigitalMailParty().partyIds(List.of(UUID.fromString(PARTY_ID))))
			.sender(new DigitalMailSender().supportInfo(new DigitalMailSenderSupportInfo()
				.text("Kontakta alkohol- och tobaksenheten")
				.emailAddress("alkohol.tobak@sundsvall.se")
				.phoneNumber("060-19 10 00")
				.url("https://sundsvall.se/alkohol")))
			.subject("Ditt serveringstillstånd")
			.department("SBK(Alkohol och tobak)")
			.contentType(TEXT_PLAIN)
			.body("Här är beviset på ditt serveringstillstånd.")
			.attachments(List.of(new DigitalMailAttachment()
				.contentType(APPLICATION_PDF)
				.content(Base64.getEncoder().encodeToString(CERTIFICATE))
				.filename("Tillståndsbevis ALKT-2026-000123.pdf"))));
		verify(messagingIntegrationMock, never()).sendSnailMail(anyString(), any());
		verify(messagingIntegrationMock).getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID);
		verify(partyAssetsIntegrationMock).findCertificate(MUNICIPALITY_ID, ASSET_ID);
		verify(supportManagementIntegrationMock, times(2)).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), any());
		verifyNoMoreInteractions(supportManagementIntegrationMock, partyAssetsIntegrationMock, messagingIntegrationMock);
	}

	@Test
	void digitalMailSentGivesTheChannelAndTheMessageId() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).supplier("Kivra").reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(sentDigitalMail());

		final var result = service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		assertThat(result).isEqualTo(new CertificateDelivery(CertificateDelivery.Channel.DIGITAL_MAIL, MESSAGE_ID.toString(), false));
	}

	@Test
	void processWithoutConfiguredLetterFailsWithoutRetryAndSendsNothing() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TOBACCO_SALES, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("No letter for the permit certificate is configured for process 'tobacco-sales'");
		verifyNoInteractions(messagingIntegrationMock);
	}

	@Test
	void sendsSnailMailToTheAddressOfTheCompanyWhenItHasNoReachableMailbox() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(false).reason("No mailbox")));
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenReturn(sentSnailMail());

		final var result = service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		assertThat(result).isEqualTo(new CertificateDelivery(CertificateDelivery.Channel.SNAIL_MAIL, MESSAGE_ID.toString(), false));
		verify(messagingIntegrationMock).sendSnailMail(MUNICIPALITY_ID, new SnailmailRequest()
			.party(new SnailmailParty().partyId(PARTY_ID))
			.address(new Address()
				.organizationName("Restaurang Norrsken AB")
				.careOf("Anna Svensson")
				.address("Storgatan 12")
				.zipCode("852 30")
				.city("Sundsvall")
				.country("Sverige"))
			.department("SBK(Alkohol och tobak)")
			.attachments(List.of(new SnailmailAttachment()
				.filename("Tillståndsbevis ALKT-2026-000123.pdf")
				.contentType("application/pdf")
				.content(Base64.getEncoder().encodeToString(CERTIFICATE)))));
		verify(messagingIntegrationMock, never()).sendDigitalMail(anyString(), anyString(), any());
		verify(messagingIntegrationMock).getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID);
		verify(partyAssetsIntegrationMock).findCertificate(MUNICIPALITY_ID, ASSET_ID);
		verify(supportManagementIntegrationMock, times(2)).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), any());
		verifyNoMoreInteractions(supportManagementIntegrationMock, partyAssetsIntegrationMock, messagingIntegrationMock);
	}

	@Test
	void snailMailToAPersonCarriesTheFirstAndLastName() {
		final var errand = errandOfCompany();
		errand.getStakeholders().getFirst().organizationName(null).careOf(null).country(null).firstName("Erik").lastName("Lindqvist");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID)).thenReturn(List.of());
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenReturn(sentSnailMail());

		service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		verify(messagingIntegrationMock).sendSnailMail(eq(MUNICIPALITY_ID), snailMailCaptor.capture());
		assertThat(snailMailCaptor.getValue().getAddress()).isEqualTo(new Address()
			.firstName("Erik")
			.lastName("Lindqvist")
			.address("Storgatan 12")
			.zipCode("852 30")
			.city("Sundsvall"));
	}

	@ParameterizedTest
	@MethodSource("incompleteAddresses")
	void noReachableMailboxAndAnIncompleteAddressFailsWithoutRetryAndSendsNothing(final Consumer<Stakeholder> incomplete) {
		final var errand = errandOfCompany();
		incomplete.accept(errand.getStakeholders().getFirst());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(false)));

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit holder of errand '%s' has no reachable mailbox and no complete address to send the permit certificate to".formatted(ERRAND_ID));
		verify(messagingIntegrationMock, never()).sendSnailMail(anyString(), any());
		verify(messagingIntegrationMock, never()).sendDigitalMail(anyString(), anyString(), any());
	}

	private static Stream<Arguments> incompleteAddresses() {
		return Stream.of(
			Arguments.of(Named.<Consumer<Stakeholder>>of("no street address", stakeholder -> stakeholder.address(null))),
			Arguments.of(Named.<Consumer<Stakeholder>>of("blank street address", stakeholder -> stakeholder.address(" "))),
			Arguments.of(Named.<Consumer<Stakeholder>>of("no zip code", stakeholder -> stakeholder.zipCode(null))),
			Arguments.of(Named.<Consumer<Stakeholder>>of("no city", stakeholder -> stakeholder.city(""))));
	}

	@Test
	void permitWithoutCertificateFailsWithoutRetryAndSendsNothing() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' has no permit certificate to send".formatted(ASSET_ID));
		verifyNoInteractions(messagingIntegrationMock);
	}

	@Test
	void digitalMailWithADeliveryThatFailedFailsWithRetry() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		final var result = sentDigitalMail();
		result.getMessages().getFirst().getDeliveries().getFirst().status(FAILED);
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(result);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY)
			.hasMessageContaining("The permit certificate of errand '%s' was not delivered as DIGITAL_MAIL".formatted(ERRAND_ID));
	}

	@Test
	void digitalMailWithOneOfTwoDeliveriesNotSentFailsWithRetry() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		final var result = sentDigitalMail();
		result.getMessages().getFirst().addDeliveriesItem(new DeliveryResult().messageType(DIGITAL_MAIL).status(NOT_SENT));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(result);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY);
	}

	@Test
	void digitalMailWithoutAnyDeliveryFailsWithRetry() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(new MessageBatchResult().messages(List.of()));

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY);
	}

	@Test
	void snailMailWithADeliveryThatWasNotSentFailsWithRetry() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID)).thenReturn(List.of());
		final var result = sentSnailMail();
		result.getDeliveries().getFirst().status(NOT_SENT);
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenReturn(result);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY)
			.hasMessageContaining("The permit certificate of errand '%s' was not delivered as SNAIL_MAIL".formatted(ERRAND_ID));
	}

	@Test
	void snailMailWithoutDeliveriesFailsWithRetry() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID)).thenReturn(List.of());
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenReturn(new MessageResult().messageId(MESSAGE_ID));

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY);
	}

	@Test
	void messagingFailingOnTheMailboxCheckFailsWithRetryAndSendsNothing() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		final var failure = new ServerProblem(BAD_GATEWAY, "messaging error: Bad Gateway");
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID)).thenThrow(failure);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isSameAs(failure);
		verify(messagingIntegrationMock, never()).sendSnailMail(anyString(), any());
		verify(messagingIntegrationMock, never()).sendDigitalMail(anyString(), anyString(), any());
	}

	@Test
	void messagingFailingToSendFailsWithRetry() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		final var failure = new ServerProblem(BAD_GATEWAY, "messaging error: Internal Server Error");
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenThrow(failure);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isSameAs(failure);
	}

	@Test
	void sentCertificateIsMarkedOnTheErrandWithTheChannelAndTheMessageId() {
		final var reread = errandOfCompany().version(5L);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany(), reread);
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(sentDigitalMail());

		service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		verify(supportManagementIntegrationMock).updateErrandParameters(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, 5L, List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2281")),
			new Parameter().key("permitCertificateDelivery").values(List.of("DIGITAL_MAIL", MESSAGE_ID.toString()))));
	}

	@Test
	void errandWithTheCertificateAlreadySentSendsNothingAndGivesTheChannelOfTheParameter() {
		final var errand = errandOfCompany().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2281")),
			new Parameter().key("permitCertificateDelivery").values(List.of("SNAIL_MAIL", MESSAGE_ID.toString()))));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);

		final var result = service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		assertThat(result).isEqualTo(new CertificateDelivery(CertificateDelivery.Channel.SNAIL_MAIL, MESSAGE_ID.toString(), true));
		verifyNoInteractions(messagingIntegrationMock, partyAssetsIntegrationMock);
		verify(supportManagementIntegrationMock, never()).updateErrandParameters(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void failedDeliveryWritesNoParameter() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID)).thenReturn(List.of());
		final var result = sentSnailMail();
		result.getDeliveries().getFirst().status(FAILED);
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenReturn(result);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(ThrowableProblem.class);
		verify(supportManagementIntegrationMock, never()).updateErrandParameters(anyString(), anyString(), anyString(), any(), any());
	}

	private static MessageResult sentSnailMail() {
		return new MessageResult()
			.messageId(MESSAGE_ID)
			.deliveries(List.of(new DeliveryResult().deliveryId(UUID.fromString("5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a")).status(SENT)));
	}

	private static MessageBatchResult sentDigitalMail() {
		return new MessageBatchResult()
			.batchId(UUID.fromString("9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b"))
			.messages(List.of(new MessageResult()
				.messageId(MESSAGE_ID)
				.deliveries(new ArrayList<>(List.of(new DeliveryResult().deliveryId(UUID.fromString("5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a")).messageType(DIGITAL_MAIL).status(SENT))))));
	}
}
