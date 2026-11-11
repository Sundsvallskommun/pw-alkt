package se.sundsvall.alkt.service;

import generated.se.sundsvall.messaging.DeliveryResult;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.messaging.MessageBatchResult;
import generated.se.sundsvall.messaging.MessageResult;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static generated.se.sundsvall.messaging.MessageStatus.FAILED;
import static generated.se.sundsvall.messaging.MessageStatus.NOT_SENT;
import static generated.se.sundsvall.messaging.MessageStatus.SENT;
import static generated.se.sundsvall.messaging.MessageType.DIGITAL_MAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING;

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
	private static final Parameter PREMISES_MUNICIPALITY = new Parameter().key("premisesMunicipalityId").values(List.of("2281"));
	private static final Parameter PENDING = new Parameter().key("permitCertificateDelivery").values(List.of("PENDING"));

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
	private ArgumentCaptor<List<Parameter>> parametersCaptor;

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
			.parameters(List.of(PREMISES_MUNICIPALITY))
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

		final var result = service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		assertThat(result).isEqualTo(new CertificateDelivery(CertificateDelivery.Channel.DIGITAL_MAIL, MESSAGE_ID.toString(), false));
		verify(messagingIntegrationMock).sendDigitalMail(eq(MUNICIPALITY_ID), eq(SUNDSVALL_ORGANIZATION_NUMBER), any());
		verify(messagingIntegrationMock).getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID);
		verify(partyAssetsIntegrationMock).findCertificate(MUNICIPALITY_ID, ASSET_ID);
		verify(supportManagementIntegrationMock, times(3)).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock, times(2)).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), parametersCaptor.capture());
		assertThat(parametersCaptor.getAllValues()).containsExactly(
			List.of(PREMISES_MUNICIPALITY, PENDING),
			List.of(PREMISES_MUNICIPALITY, new Parameter().key("permitCertificateDelivery").values(List.of("DIGITAL_MAIL", MESSAGE_ID.toString()))));
		verifyNoMoreInteractions(supportManagementIntegrationMock, partyAssetsIntegrationMock, messagingIntegrationMock);
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
		verify(messagingIntegrationMock).sendSnailMail(eq(MUNICIPALITY_ID), any());
		verify(messagingIntegrationMock, never()).sendDigitalMail(anyString(), anyString(), any());
		verify(supportManagementIntegrationMock, times(2)).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), parametersCaptor.capture());
		assertThat(parametersCaptor.getAllValues()).containsExactly(
			List.of(PREMISES_MUNICIPALITY, PENDING),
			List.of(PREMISES_MUNICIPALITY, new Parameter().key("permitCertificateDelivery").values(List.of("SNAIL_MAIL", MESSAGE_ID.toString()))));
	}

	@Test
	void digitalMailThatFailedFallsBackToSnailMail() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		final var digitalMail = sentDigitalMail();
		digitalMail.getMessages().getFirst().getDeliveries().getFirst().status(FAILED);
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(digitalMail);
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenReturn(sentSnailMail());

		final var result = service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		assertThat(result).isEqualTo(new CertificateDelivery(CertificateDelivery.Channel.SNAIL_MAIL, MESSAGE_ID.toString(), false));
		verify(messagingIntegrationMock).sendDigitalMail(eq(MUNICIPALITY_ID), eq(SUNDSVALL_ORGANIZATION_NUMBER), any());
		verify(messagingIntegrationMock).sendSnailMail(eq(MUNICIPALITY_ID), any());
	}

	@Test
	void digitalMailThatFailedWithoutAnAddressFailsWithRetryAndRemovesThePendingMarker() {
		final var errand = errandOfCompany();
		errand.getStakeholders().getFirst().address(null);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		final var digitalMail = sentDigitalMail();
		digitalMail.getMessages().getFirst().addDeliveriesItem(new DeliveryResult().messageType(DIGITAL_MAIL).status(NOT_SENT));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(digitalMail);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY)
			.hasMessageContaining("The permit certificate of errand '%s' was not delivered as DIGITAL_MAIL, delivery statuses [SENT, NOT_SENT]".formatted(ERRAND_ID));
		verify(messagingIntegrationMock, never()).sendSnailMail(anyString(), any());
		verify(supportManagementIntegrationMock, times(2)).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), parametersCaptor.capture());
		assertThat(parametersCaptor.getAllValues()).containsExactly(List.of(PREMISES_MUNICIPALITY, PENDING), List.of(PREMISES_MUNICIPALITY));
	}

	@ParameterizedTest
	@MethodSource("incompleteRecipients")
	void noReachableMailboxAndAnIncompleteRecipientFailsWithoutRetryAndSendsNothing(final Consumer<Stakeholder> incomplete) {
		final var errand = errandOfCompany();
		incomplete.accept(errand.getStakeholders().getFirst());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(false)));

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit holder of errand '%s' has no reachable mailbox and no complete name and address to send the permit certificate to".formatted(ERRAND_ID));
		verify(messagingIntegrationMock, never()).sendSnailMail(anyString(), any());
		verify(messagingIntegrationMock, never()).sendDigitalMail(anyString(), anyString(), any());
		verify(supportManagementIntegrationMock, never()).updateErrandParameters(anyString(), anyString(), anyString(), any(), any());
	}

	private static Stream<Arguments> incompleteRecipients() {
		return Stream.of(
			Arguments.of(Named.<Consumer<Stakeholder>>of("no name", stakeholder -> stakeholder.organizationName(null))),
			Arguments.of(Named.<Consumer<Stakeholder>>of("first name without last name", stakeholder -> stakeholder.organizationName(" ").firstName("Anna"))),
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
	void snailMailThatWasNotSentFailsWithRetryAndRemovesThePendingMarker() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID)).thenReturn(List.of());
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenReturn(new MessageResult().messageId(MESSAGE_ID));

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY)
			.hasMessageContaining("The permit certificate of errand '%s' was not delivered as SNAIL_MAIL, delivery statuses []".formatted(ERRAND_ID));
		verify(supportManagementIntegrationMock, times(2)).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), parametersCaptor.capture());
		assertThat(parametersCaptor.getAllValues()).containsExactly(List.of(PREMISES_MUNICIPALITY, PENDING), List.of(PREMISES_MUNICIPALITY));
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
		verify(supportManagementIntegrationMock, never()).updateErrandParameters(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void digitalMailFailingWithAnErrorFallsBackToSnailMail() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any()))
			.thenThrow(new ServerProblem(BAD_GATEWAY, "messaging error: {detail=Kivra refused the message, status=500 Internal Server Error, title=Internal Server Error}"));
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenReturn(sentSnailMail());

		final var result = service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		assertThat(result).isEqualTo(new CertificateDelivery(CertificateDelivery.Channel.SNAIL_MAIL, MESSAGE_ID.toString(), false));
		verify(supportManagementIntegrationMock, times(2)).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), parametersCaptor.capture());
		assertThat(parametersCaptor.getAllValues()).containsExactly(
			List.of(PREMISES_MUNICIPALITY, PENDING),
			List.of(PREMISES_MUNICIPALITY, new Parameter().key("permitCertificateDelivery").values(List.of("SNAIL_MAIL", MESSAGE_ID.toString()))));
	}

	@Test
	void letterRefusedAfterADigitalMailWithAnUnknownOutcomeFailsWithoutRetryAndKeepsThePendingMarker() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenThrow(new ServerProblem(BAD_GATEWAY, "messaging error: Gateway Timeout"));
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenThrow(new ClientProblem(BAD_REQUEST, "messaging error: Bad Request"));

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit certificate of errand '%s' may have been sent as digital mail, whose outcome is unknown, and the letter after it failed: ClientProblem 400 from messaging"
				.formatted(ERRAND_ID));
		verify(supportManagementIntegrationMock).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), parametersCaptor.capture());
		assertThat(parametersCaptor.getAllValues()).containsExactly(List.of(PREMISES_MUNICIPALITY, PENDING));
	}

	@Test
	void messagingRefusingTheLetterFailsWithRetryAndRemovesThePendingMarker() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID)).thenReturn(List.of());
		final var failure = new ClientProblem(BAD_REQUEST, "messaging error: Bad Request");
		when(messagingIntegrationMock.sendSnailMail(anyString(), any())).thenThrow(failure);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isSameAs(failure);
		verify(supportManagementIntegrationMock, times(2)).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), parametersCaptor.capture());
		assertThat(parametersCaptor.getAllValues()).containsExactly(List.of(PREMISES_MUNICIPALITY, PENDING), List.of(PREMISES_MUNICIPALITY));
	}

	@Test
	void digitalMailWithAnUnknownOutcomeAndNoAddressFailsWithoutRetryAndKeepsThePendingMarker() {
		final var errand = errandOfCompany();
		errand.getStakeholders().getFirst().address(null);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenThrow(new ServerProblem(BAD_GATEWAY, "messaging error: Gateway Timeout"));

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit certificate of errand '%s' may have been sent, as the answer from messaging does not tell: ServerProblem 502 from messaging".formatted(ERRAND_ID));
		verify(supportManagementIntegrationMock).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(4L), parametersCaptor.capture());
		assertThat(parametersCaptor.getAllValues()).containsExactly(List.of(PREMISES_MUNICIPALITY, PENDING));
		verify(messagingIntegrationMock, never()).sendSnailMail(anyString(), any());
	}

	@Test
	void errandWithAPendingMarkerFailsWithoutRetryAndSendsNothing() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany().parameters(List.of(PREMISES_MUNICIPALITY, PENDING)));

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("""
				The permit certificate of errand '%s' may already have been sent, as an earlier attempt did not learn whether it was. \
				Check in messaging whether it reached the permit holder. If it did, set errand parameter 'permitCertificateDelivery' \
				to the channel and the message id, otherwise remove it to send the certificate again""".formatted(ERRAND_ID));
		verifyNoInteractions(messagingIntegrationMock, partyAssetsIntegrationMock);
		verify(supportManagementIntegrationMock, never()).updateErrandParameters(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void sentCertificateIsMarkedWithTheVersionOfAFreshReadSoAChangeMadeMeanwhileIsKept() {
		final var reread = errandOfCompany().version(6L).parameters(List.of(
			PREMISES_MUNICIPALITY,
			PENDING,
			new Parameter().key("premisesName").values(List.of("Restaurang Norrsken"))));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany(), errandOfCompany(), reread);
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(sentDigitalMail());

		service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		verify(supportManagementIntegrationMock).updateErrandParameters(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, 6L, List.of(
			PREMISES_MUNICIPALITY,
			new Parameter().key("premisesName").values(List.of("Restaurang Norrsken")),
			new Parameter().key("permitCertificateDelivery").values(List.of("DIGITAL_MAIL", MESSAGE_ID.toString()))));
	}

	@Test
	void markerWriteAnsweredWith412IsRetriedOnAFreshReadWithoutSendingAgain() {
		final var reread = errandOfCompany().version(7L).parameters(List.of(PREMISES_MUNICIPALITY, PENDING));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany(), errandOfCompany(), errandOfCompany(), reread);
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(sentDigitalMail());
		doNothing()
			.doThrow(new ClientProblem(PRECONDITION_FAILED, "support-management error: Precondition Failed"))
			.doNothing()
			.when(supportManagementIntegrationMock).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any(), any());

		service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		verify(messagingIntegrationMock).sendDigitalMail(eq(MUNICIPALITY_ID), eq(SUNDSVALL_ORGANIZATION_NUMBER), any());
		verify(supportManagementIntegrationMock).updateErrandParameters(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, 7L, List.of(
			PREMISES_MUNICIPALITY,
			new Parameter().key("permitCertificateDelivery").values(List.of("DIGITAL_MAIL", MESSAGE_ID.toString()))));
	}

	@Test
	void sentCertificateThatCannotBeMarkedOnTheErrandFailsWithoutRetryAndIsSentOnce() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandOfCompany());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(CERTIFICATE));
		when(messagingIntegrationMock.getMailboxes(MUNICIPALITY_ID, SUNDSVALL_ORGANIZATION_NUMBER, PARTY_ID))
			.thenReturn(List.of(new Mailbox().partyId(PARTY_ID).reachable(true)));
		when(messagingIntegrationMock.sendDigitalMail(anyString(), anyString(), any())).thenReturn(sentDigitalMail());
		doNothing()
			.doThrow(new ServerProblem(BAD_GATEWAY, "support-management error: {detail=Database is down, status=503 Service Unavailable, title=Service Unavailable}"))
			.when(supportManagementIntegrationMock).updateErrandParameters(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any(), any());

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit certificate of errand '%s' was sent as DIGITAL_MAIL with message id '%s' but could not be marked on the errand: ServerProblem 502 from support-management (remote 503 Service Unavailable)"
				.formatted(ERRAND_ID, MESSAGE_ID));
		verify(messagingIntegrationMock).sendDigitalMail(eq(MUNICIPALITY_ID), eq(SUNDSVALL_ORGANIZATION_NUMBER), any());
	}

	@Test
	void errandWithTheCertificateAlreadySentSendsNothingAndGivesTheChannelOfTheParameter() {
		final var errand = errandOfCompany().parameters(List.of(
			PREMISES_MUNICIPALITY,
			new Parameter().key("permitCertificateDelivery").values(List.of("SNAIL_MAIL", MESSAGE_ID.toString()))));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);

		final var result = service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID);

		assertThat(result).isEqualTo(new CertificateDelivery(CertificateDelivery.Channel.SNAIL_MAIL, MESSAGE_ID.toString(), true));
		verifyNoInteractions(messagingIntegrationMock, partyAssetsIntegrationMock);
		verify(supportManagementIntegrationMock, never()).updateErrandParameters(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void errandWithAnUnknownChannelInTheParameterFailsWithoutRetryAndSendsNothing() {
		final var errand = errandOfCompany().parameters(List.of(
			new Parameter().key("permitCertificateDelivery").values(List.of("FAX", MESSAGE_ID.toString()))));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);

		assertThatThrownBy(() -> service.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING, ASSET_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand parameter 'permitCertificateDelivery' of errand '%s' holds an unknown channel".formatted(ERRAND_ID));
		verifyNoInteractions(messagingIntegrationMock, partyAssetsIntegrationMock);
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
