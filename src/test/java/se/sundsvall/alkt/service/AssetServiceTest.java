package se.sundsvall.alkt.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetJsonParameter;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.configuration.PermitJsonProperties;
import se.sundsvall.alkt.configuration.PermitJsonProperties.Settings;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.partyassets.model.VersionedAsset;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;
import static se.sundsvall.alkt.service.model.AssetType.ALCOHOL_SERVING_PERMIT;
import static se.sundsvall.alkt.service.model.AssetType.LOW_ALCOHOL_BEER_SALES_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.TOBACCO_SALES_PERMIT;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String ERRAND_NUMBER = "ALKT-2026-000123";
	private static final String DECISION_ID = "decision-id";
	private static final String PARTY_ID = "party-id";
	private static final String ASSET_ID = "9c8b7a6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d";
	private static final String CERTIFICATE_TEMPLATE = "serving-permit-certificate";
	private static final String ALCOHOL_DECISION_KEY = "aot_alcohol_decision";
	private static final String TOBACCO_DECISION_KEY = "aot_tobacco_decision";
	private static final String ALCOHOL_PERMIT_KEY = "aot_alcohol_party_asset";
	private static final String ALCOHOL_SCHEMA_ID = "2281_aot_alcohol_party_asset_0.1";
	private static final String VERSION = "\"3\"";
	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
	private static final OffsetDateTime DECISION_CREATED = OffsetDateTime.of(2026, 10, 5, 9, 0, 0, 0, ZoneOffset.UTC);
	private static final String CERTIFICATE_COPY = "Tillståndsbevis ALKT-2026-000123.pdf";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private PartyAssetsIntegration partyAssetsIntegrationMock;

	@Mock
	private TemplatingIntegration templatingIntegrationMock;

	@Captor
	private ArgumentCaptor<AssetCreateRequest> assetCaptor;

	@Captor
	private ArgumentCaptor<AssetFile> attachmentCaptor;

	@Captor
	private ArgumentCaptor<AssetUpdateRequest> updateCaptor;

	@Captor
	private ArgumentCaptor<Map<String, Object>> templateParametersCaptor;

	private AssetService assetService;

	@BeforeEach
	void setUp() {
		assetService = new AssetService(supportManagementIntegrationMock, partyAssetsIntegrationMock, templatingIntegrationMock,
			new PermitJsonProperties(new Settings("0.1", ALCOHOL_DECISION_KEY), new Settings("1.0", TOBACCO_DECISION_KEY)),
			Clock.fixed(TODAY.atTime(12, 0).atZone(SWEDISH_TIME).toInstant(), SWEDISH_TIME));
	}

	@Test
	void findOrCreateAssetCreatesADraftAttachesTheFilesOneAtATimeAndActivatesIt() throws IOException {
		final var first = new ErrandAttachment().id("first").fileName("beslut.pdf").mimeType("application/pdf");
		final var second = new ErrandAttachment().id("second").fileName("ritning.pdf").mimeType("application/pdf");
		final var content = "file".getBytes();

		givenADraftFor(approval().type("PERMIT").attachments(List.of(first, second)));
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first")).thenReturn(content);
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second")).thenReturn(content);

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null)).isEqualTo(ASSET_ID);

		final InOrder inOrder = inOrder(supportManagementIntegrationMock, partyAssetsIntegrationMock);
		inOrder.verify(partyAssetsIntegrationMock).createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), assetCaptor.capture());
		inOrder.verify(supportManagementIntegrationMock).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first");
		inOrder.verify(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());
		inOrder.verify(supportManagementIntegrationMock).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second");
		inOrder.verify(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(partyAssetsIntegrationMock, never()).removeDraftAsset(any(), any());

		assertThat(assetCaptor.getValue().getAssetId()).isEqualTo(DECISION_ID);
		assertThat(assetCaptor.getValue().getPartyId()).isEqualTo(PARTY_ID);
		assertThat(assetCaptor.getValue().getType()).isEqualTo(LOW_ALCOHOL_BEER_SALES_NOTIFICATION.getValue());
		assertThat(attachmentCaptor.getAllValues()).extracting(file -> file.file().getOriginalFilename()).containsExactly("beslut.pdf", "ritning.pdf");
		assertThat(attachmentCaptor.getAllValues().getFirst().file().getBytes()).isEqualTo(content);
		verifyNoInteractions(templatingIntegrationMock);
	}

	/** A notification has no decision of the case worker, so it keeps the parameters of its decision. */
	@Test
	void findOrCreateAssetGivesANotificationTheParametersOfItsDecisionAndNoJsonParameter() {
		givenADraftFor(approval().legalBasis("8 kap. 6 § alkohollagen"));

		assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null);

		verify(partyAssetsIntegrationMock).createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), assetCaptor.capture());
		assertThat(assetCaptor.getValue().getAdditionalParameters()).containsEntry("errandId", ERRAND_ID).containsEntry("legalBasis", "8 kap. 6 § alkohollagen");
		assertThat(assetCaptor.getValue().getJsonParameters()).isNull();
		verify(supportManagementIntegrationMock, never()).getDecisionJson(any(), any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetBuildsThePermitFromTheJsonOfTheDecisionAndAttachesItsCertificateBeforeActivating() throws IOException {
		final var attachment = new ErrandAttachment().id("first").fileName("beslut.pdf").mimeType("application/pdf");
		final var pdf = "%PDF-1.7".getBytes();

		givenAPermitDraftFor(approval()
			.title("Beslut om serveringstillstånd")
			.attachments(List.of(attachment))
			.terms(List.of(new DecisionTerm().sortOrder(2).text("Bordsservering ute."), new DecisionTerm().sortOrder(1).text("Ordningsvakt efter 23.00."))), ALCOHOL_DECISION_KEY, """
				{
				  "errandId": "another-errand",
				  "caseRegistrationNumber": "ANOTHER-NUMBER",
				  "permitHolder": { "name": "Runt Hörnet AB" },
				  "premises": { "name": "Runt Hörnet", "restaurantNumber": "22819999", "newRestaurantNumber": true },
				  "conditions": ["Från beslutets JSON."]
				}
				""");
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first")).thenReturn("file".getBytes());
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture())).thenReturn(pdf);

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, "22810001")).isEqualTo(ASSET_ID);

		final InOrder inOrder = inOrder(supportManagementIntegrationMock, templatingIntegrationMock, partyAssetsIntegrationMock);
		inOrder.verify(supportManagementIntegrationMock).getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, ALCOHOL_DECISION_KEY);
		inOrder.verify(templatingIntegrationMock).renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any());
		inOrder.verify(partyAssetsIntegrationMock).createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), assetCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock, times(2)).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);

		final var request = assetCaptor.getValue();
		assertThat(request.getAssetId()).isEqualTo(DECISION_ID);
		assertThat(request.getType()).isEqualTo(ALCOHOL_SERVING_PERMIT.getValue());
		assertThat(request.getTitle()).isEqualTo("Beslut om serveringstillstånd");
		assertThat(request.getAdditionalParameters()).isNull();
		assertThat(request.getJsonParameters()).singleElement().satisfies(parameter -> {
			assertThat(parameter.getKey()).isEqualTo(ALCOHOL_PERMIT_KEY);
			assertThat(parameter.getSchemaId()).isEqualTo(ALCOHOL_SCHEMA_ID);
			assertThat(parameter.getValue()).isEqualTo(json("""
				{
				  "errandId": "errand-id",
				  "caseRegistrationNumber": "ALKT-2026-000123",
				  "permitHolder": { "name": "Runt Hörnet AB" },
				  "premises": { "name": "Runt Hörnet", "restaurantNumber": "22810001" },
				  "conditions": ["Ordningsvakt efter 23.00.", "Bordsservering ute."],
				  "decision": { "caseRegistrationNumber": "ALKT-2026-000123" }
				}
				"""));
		});
		assertThat(templateParametersCaptor.getValue())
			.containsEntry("caseNumber", ERRAND_NUMBER)
			.containsEntry("permitHolderName", "Runt Hörnet AB")
			.containsEntry("premisesName", "Runt Hörnet")
			.containsEntry("premisesRestaurantNumber", "22810001")
			.containsEntry("conditions", "Ordningsvakt efter 23.00.\nBordsservering ute.");

		final var certificate = attachmentCaptor.getAllValues().getLast();
		assertThat(certificate.file().getOriginalFilename()).isEqualTo("tillstandsbevis.pdf");
		assertThat(certificate.file().getBytes()).isEqualTo(pdf);
		assertThat(certificate.category()).isEqualTo("Tillståndsbevis");
	}

	@Test
	void findOrCreateAssetBuildsATobaccoPermitByTheTobaccoSchema() {
		givenAPermitDraftFor(approval(), TOBACCO_DECISION_KEY, """
			{ "pointOfSale": { "name": "Tobaksaffären" } }
			""");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq("tobacco-permit-certificate"), templateParametersCaptor.capture())).thenReturn("%PDF-1.7".getBytes());

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TOBACCO_SALES_PERMIT, null)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), assetCaptor.capture());
		assertThat(assetCaptor.getValue().getJsonParameters()).singleElement().satisfies(parameter -> {
			assertThat(parameter.getKey()).isEqualTo("aot_tobacco_party_asset");
			assertThat(parameter.getSchemaId()).isEqualTo("2281_aot_tobacco_party_asset_1.0");
			assertThat(parameter.getValue().path("pointOfSale").path("name").asString()).isEqualTo("Tobaksaffären");
		});
		assertThat(templateParametersCaptor.getValue()).containsEntry("premisesName", "Tobaksaffären");
	}

	/**
	 * The copy goes up once the permit is active, so an errand never shows the certificate of a permit that was not made.
	 */
	@Test
	void findOrCreateAssetKeepsTheCertificateOnTheErrandOnceThePermitIsActive() {
		final var pdf = "%PDF-1.7".getBytes();
		givenAPermitDraftFor(approval().created(DECISION_CREATED), ALCOHOL_DECISION_KEY, "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn(pdf);
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("decision-basis").fileName("Beslutsunderlag ALKT-2026-000123.pdf").created(DECISION_CREATED.minusDays(1))));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, "22810001")).isEqualTo(ASSET_ID);

		final InOrder inOrder = inOrder(supportManagementIntegrationMock, partyAssetsIntegrationMock);
		inOrder.verify(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		inOrder.verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		inOrder.verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_COPY, pdf);
		assertThat(attachmentCaptor.getValue().category()).isEqualTo("Tillståndsbevis");
		verify(partyAssetsIntegrationMock, never()).findCertificate(any(), any());
	}

	@Test
	void findOrCreateAssetNamesTheCopyOnTheErrandForTheCaseNumberOfTheCertificateWithoutItsSlash() {
		final var pdf = "%PDF-1.7".getBytes();
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder()
			.parameters(List.of(new Parameter().key("premisesMunicipalityId").values(List.of("2262")), new Parameter().key("externalCaseRegistrationNumber").values(List.of("TSN 2026/0153")))));
		givenTheDecisionJson(TOBACCO_DECISION_KEY, "{}");
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());
		when(partyAssetsIntegrationMock.createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any())).thenReturn(ASSET_ID);
		when(templatingIntegrationMock.renderPdf(eq("2262"), eq("tobacco-permit-certificate"), templateParametersCaptor.capture())).thenReturn(pdf);

		assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TOBACCO_SALES_PERMIT, null);

		assertThat(templateParametersCaptor.getValue()).containsEntry("caseNumber", "TSN 2026/0153");
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "Tillståndsbevis TSN 2026-0153.pdf", pdf);
	}

	/** The permit is active when the copy is made, so a copy that fails leaves it for the rerun to find. */
	@Test
	void findOrCreateAssetLeavesTheActivePermitInPlaceWhenTheCopyCannotBeUploaded() {
		final var failure = Problem.valueOf(BAD_GATEWAY, "Support management is down");
		givenAPermitDraftFor(approval(), ALCOHOL_DECISION_KEY, "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());
		when(supportManagementIntegrationMock.createPdfAttachment(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(CERTIFICATE_COPY), any())).thenThrow(failure);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null)).isSameAs(failure);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(partyAssetsIntegrationMock, never()).removeDraftAsset(any(), any());
	}

	/**
	 * A rerun after the permit was made may come from a copy that failed, so it copies the certificate party-assets holds.
	 */
	@Test
	void findOrCreateAssetCopiesTheCertificateOfThePermitAlreadyMadeToTheErrand() {
		final var pdf = "%PDF-1.7".getBytes();
		givenThePermitAlreadyMadeFor(approval().created(DECISION_CREATED));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("decision-basis").fileName("Beslutsunderlag ALKT-2026-000123.pdf").created(DECISION_CREATED.plusMinutes(5))));
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(pdf));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null)).isEqualTo(ASSET_ID);

		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_COPY, pdf);
		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	/** A copy uploaded since the decision was created is the one an earlier attempt made. */
	@ParameterizedTest
	@ValueSource(longs = {
		0, 1
	})
	void findOrCreateAssetUploadsNoSecondCopyOfTheCertificate(final long secondsAfterTheDecision) {
		givenThePermitAlreadyMadeFor(approval().created(DECISION_CREATED));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(certificateCopy(DECISION_CREATED.plusSeconds(secondsAfterTheDecision))));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock, never()).findCertificate(any(), any());
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(any(), any(), any(), any(), any());
	}

	/** A copy from before the decision belongs to an earlier decision of the errand. */
	@Test
	void findOrCreateAssetCopiesTheCertificateBesideTheCopyOfAnEarlierDecision() {
		final var pdf = "%PDF-1.7".getBytes();
		givenThePermitAlreadyMadeFor(approval().created(DECISION_CREATED));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(certificateCopy(DECISION_CREATED.minusSeconds(1))));
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(pdf));

		assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null);

		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_COPY, pdf);
	}

	@Test
	void findOrCreateAssetTakesAnyCopyOfTheNameForTheCopyOfADecisionWithoutCreationTime() {
		givenThePermitAlreadyMadeFor(approval());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(certificateCopy(DECISION_CREATED.minusYears(1))));

		assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null);

		verify(partyAssetsIntegrationMock, never()).findCertificate(any(), any());
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(any(), any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetCopiesNothingOfAPermitAlreadyMadeWithoutACertificate() {
		givenThePermitAlreadyMadeFor(approval());
		when(partyAssetsIntegrationMock.findCertificate(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.empty());

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null)).isEqualTo(ASSET_ID);

		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(any(), any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetKeepsNoCertificateOnTheErrandOfANotification() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID))
			.thenReturn(Optional.of(activeAsset().type(LOW_ALCOHOL_BEER_SALES_NOTIFICATION.getValue())));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null)).isEqualTo(ASSET_ID);

		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyAssetsIntegrationMock).findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock, partyAssetsIntegrationMock);
	}

	@Test
	void findOrCreateAssetKeepsNoCertificateOnTheErrandOfANewNotification() {
		givenADraftFor(approval());

		assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(supportManagementIntegrationMock, never()).getAttachments(any(), any(), any());
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(any(), any(), any(), any(), any());
	}

	/**
	 * The permit has the own case number also in Timrå, whose number the case worker gives in the decision. The
	 * certificate is Timrå's and shows the number Timrå has for the deciding errand.
	 */
	@Test
	void findOrCreateAssetOfAPremisesInTimraHasTheOwnCaseNumberAndTheCertificateTheOneOfTimra() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder()
			.parameters(List.of(new Parameter().key("premisesMunicipalityId").values(List.of("2262")), new Parameter().key("externalCaseRegistrationNumber").values(List.of("TIMRA-1")))));
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, """
			{ "externalCaseRegistrationNumber": "2026/0456", "premises": { "municipalityId": "2262" } }
			""");
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());
		when(partyAssetsIntegrationMock.createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any())).thenReturn(ASSET_ID);
		final var pdf = "%PDF-1.7".getBytes();
		when(templatingIntegrationMock.renderPdf(eq("2262"), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture())).thenReturn(pdf);

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, "22620001")).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), assetCaptor.capture());
		final var permit = assetCaptor.getValue().getJsonParameters().getFirst();
		assertThat(permit.getSchemaId()).isEqualTo(ALCOHOL_SCHEMA_ID);
		assertThat(permit.getValue().path("caseRegistrationNumber").asString()).isEqualTo(ERRAND_NUMBER);
		assertThat(permit.getValue().path("externalCaseRegistrationNumber").asString()).isEqualTo("2026/0456");
		assertThat(templateParametersCaptor.getValue()).containsEntry("caseNumber", "TIMRA-1");
		// party-assets keeps every permit under the municipality of the call, the certificate is Timrå's own
		verify(partyAssetsIntegrationMock).findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID);
		verify(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), any());
		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, ALCOHOL_DECISION_KEY);
		// The copy on the errand is named for the number the certificate shows
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "Tillståndsbevis TIMRA-1.pdf", pdf);
		verifyNoMoreInteractions(partyAssetsIntegrationMock, templatingIntegrationMock, supportManagementIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsWithoutRetryAndCreatesNoDraftForAnErrandInTimraWithoutItsCaseNumber() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder()
			.id(ERRAND_ID)
			.parameters(List.of(new Parameter().key("premisesMunicipalityId").values(List.of("2262")))));
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, "{}");
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' in Timrå has no case number in externalCaseRegistrationNumber");

		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsWithoutRetryAndCreatesNoDraftWhenTheDecisionHasNoJsonParameter() {
		givenNoAssetYetFor(approval());
		when(supportManagementIntegrationMock.getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, ALCOHOL_DECISION_KEY))
			.thenThrow(new NonRetryableException("Decision 'decision-id' of errand 'errand-id' has no JSON parameter 'aot_alcohol_decision'"));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("has no JSON parameter 'aot_alcohol_decision'");

		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void findOrCreateAssetFailsWithoutRetryAndCreatesNoDraftWhenTheErrandHasNoErrandNumber(final String errandNumber) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder().errandNumber(errandNumber));
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, "{}");

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no errand number to give the permit as its case number");

		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
	}

	/** party-assets validates the JSON parameter as the draft is created, so a refused one leaves no draft behind. */
	@Test
	void findOrCreateAssetFailsWithoutRetryAndLeavesNoDraftWhenPartyAssetsRefusesTheJsonParameter() {
		givenNoAssetYetFor(approval());
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, """
			{ "premises": { "restaurantNumber": "123" } }
			""");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());
		when(partyAssetsIntegrationMock.createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any()))
			.thenThrow(new NonRetryableException("The draft asset of errand 'errand-id' cannot be created: ClientProblem 400"));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("cannot be created");

		verify(partyAssetsIntegrationMock, never()).addAttachmentToDraft(any(), any(), any());
		verify(partyAssetsIntegrationMock, never()).activateAsset(any(), any());
		verify(partyAssetsIntegrationMock, never()).removeDraftAsset(any(), any());
	}

	/**
	 * The conditions come from the terms only, so conditions in the JSON of the decision do not make up for missing terms.
	 */
	@Test
	void findOrCreateAssetCreatesNoDraftOfAnApprovalWithConditionsThatHasConditionsInItsJsonButNoTerms() {
		givenNoAssetYetFor(approval().outcome("APPROVAL_WITH_CONDITIONS"));
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, """
			{ "conditions": ["Ordningsvakt efter 23.00."] }
			""");

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("approval with conditions but has no conditions");

		verifyNoInteractions(templatingIntegrationMock);
		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
	}

	private static Stream<RuntimeException> renderFailures() {
		return Stream.of(new ClientProblem(BAD_GATEWAY, "Templating is down"), new NonRetryableException("Missing template parameter 'premisesName'"));
	}

	/** The certificate is rendered before the draft, so a render that fails, for whatever reason, leaves no draft. */
	@ParameterizedTest
	@MethodSource("renderFailures")
	void findOrCreateAssetCreatesNoDraftWhenTheCertificateCannotBeRendered(final RuntimeException failure) {
		givenNoAssetYetFor(approval());
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenThrow(failure);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null)).isSameAs(failure);

		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
	}

	/** The asset id is the only way to find the draft left behind, so it travels with the incident as well. */
	@Test
	void findOrCreateAssetCarriesTheAssetIdWithoutRetryWhenTheDraftOfARefusedActivationCannotBeRemoved() {
		givenADraftFor(approval());
		doThrow(new NonRetryableException("Asset '%s' cannot be activated".formatted(ASSET_ID))).when(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("Draft asset '%s'".formatted(ASSET_ID))
			.hasMessageContaining("cannot be activated")
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Party assets is down");
	}

	@Test
	void findOrCreateAssetAnswersWithTheExistingAssetOfTheDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.of(activeAsset().id("existing-asset-id")));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null)).isEqualTo("existing-asset-id");

		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getAttachment(any(), any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getDecisionJson(any(), any(), any(), any(), any());
	}

	/** The permit was created when its decision still ended after today, so a rerun does not check it again. */
	@Test
	void findOrCreateAssetAnswersWithTheExistingAssetOfADecisionThatHasEnded() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().validTo(TODAY.minusDays(1))));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.of(activeAsset().id("existing-asset-id")));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null)).isEqualTo("existing-asset-id");
	}

	@Test
	void findOrCreateAssetFailsWithoutRetryOnAnExistingAssetOfAnotherType() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.of(activeAsset().type("PERMIT")));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' is of type 'PERMIT', the process handles only type '%s'".formatted(ASSET_ID, ALCOHOL_SERVING_PERMIT.getValue()));

		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetCreatesAnAssetThatEndsAfterToday() {
		givenADraftFor(approval().validTo(TODAY.plusDays(2)));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@ParameterizedTest
	@ValueSource(longs = {
		0, -1
	})
	void findOrCreateAssetFailsWithoutRetryOnADecisionThatDoesNotEndAfterToday(final long daysFromToday) {
		final var validTo = TODAY.plusDays(daysFromToday);
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().validTo(validTo)));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Decision of errand 'errand-id' is valid to %s, which is not after today, so the permit cannot be activated".formatted(validTo));

		verifyNoMoreInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void findOrCreateAssetWithoutAttachmentsOnTheDecision() {
		givenADraftFor(approval().attachments(null));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(partyAssetsIntegrationMock, never()).addAttachmentToDraft(any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getAttachment(any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenAnAttachmentCannotBeFetched() {
		givenADraftFor(approval().attachments(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second"))));
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first"))
			.thenThrow(Problem.valueOf(BAD_GATEWAY, "Support management is down"));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("Support management is down");

		verify(supportManagementIntegrationMock, never()).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second");
		verify(partyAssetsIntegrationMock, never()).addAttachmentToDraft(any(), any(), any());
		verify(partyAssetsIntegrationMock, never()).activateAsset(any(), any());
		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenAnAttachmentFails() {
		givenADraftFor(approval().attachments(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second"))));
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first")).thenReturn("file".getBytes());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), any());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Party assets is down");

		verify(supportManagementIntegrationMock, never()).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second");
		verify(partyAssetsIntegrationMock, never()).activateAsset(any(), any());
		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenTheActivationFails() {
		givenADraftFor(approval());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Party assets is down");

		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void findOrCreateAssetCarriesTheAssetIdWhenTheDraftCannotBeRemoved() {
		givenADraftFor(approval());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is still down")).when(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(ASSET_ID)
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Party assets is down")
			.hasMessageNotContaining("Party assets is still down");
	}

	@Test
	void findOrCreateAssetFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no completed decision");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	/** An approval with conditions grants the permit as well, so it is created the same way. */
	@Test
	void findOrCreateAssetCreatesThePermitOfAnApprovalWithConditions() {
		givenAPermitDraftFor(approval().outcome("APPROVAL_WITH_CONDITIONS").terms(List.of(new DecisionTerm().sortOrder(1).text("Ordningsvakt efter 23.00."))),
			ALCOHOL_DECISION_KEY, "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture())).thenReturn("%PDF-1.7".getBytes());

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		assertThat(templateParametersCaptor.getValue()).containsEntry("conditions", "Ordningsvakt efter 23.00.");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"REJECTED", "DISMISSED", "INADMISSIBLE"
	})
	void findOrCreateAssetFailsOnAnOutcomeThatGrantsNoPermit(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().id(DECISION_ID).outcome(outcome)));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("only created or changed from one of [APPROVAL, APPROVAL_WITH_CONDITIONS]");

		verifyNoInteractions(partyAssetsIntegrationMock);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsOnADecisionWithoutId() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().id(null)));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no id");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(STAKEHOLDER_ROLE_PERMIT_HOLDER);

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void updateAssetPutsTheChangeOnThePermitRendersTheCertificateBeforeItPatchesTheAssetAndReplacesTheCertificate() throws IOException {
		final var pdf = "%PDF-1.7".getBytes();
		final var other = new AssetJsonParameter().key("other").schemaId("2281_other_1.0").value(json("{}"));
		final var asset = activeAsset().jsonParameters(List.of(permitParameter("""
			{
			  "errandId": "granting-errand-id",
			  "caseRegistrationNumber": "ALKT-2026-000001",
			  "permitHolder": { "name": "Runt Hörnet AB" },
			  "premises": { "name": "Runt Hörnet", "restaurantNumber": "22810001", "phoneNumber": "060-123456" },
			  "servingHours": [{ "from": "11:00", "to": "01:00" }],
			  "conditions": ["Ordningsvakt efter 23.00."],
			  "decision": { "caseRegistrationNumber": "ALKT-2026-000001", "decisionText": "Bifall med villkor" }
			}
			"""), other));

		givenAnErrandNaming(approval()
			.validTo(LocalDate.of(2099, 9, 30))
			.terms(List.of(new DecisionTerm().sortOrder(1).text("Ordningsvakt efter 01.00."))));
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, """
			{
			  "errandId": "change-errand",
			  "premises": { "name": "Runt Hörnet Bar", "restaurantNumber": "22819999", "newRestaurantNumber": true },
			  "servingHours": [{ "from": "11:00", "to": "02:00" }],
			  "decision": { "decisionText": "Bifall" }
			}
			""");
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(asset));
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn(pdf);

		assertThat(assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		final InOrder inOrder = inOrder(partyAssetsIntegrationMock, templatingIntegrationMock);
		inOrder.verify(templatingIntegrationMock).renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).replaceCertificate(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());

		final var update = updateCaptor.getValue();
		assertThat(update.getValidTo()).isEqualTo(LocalDate.of(2099, 9, 30));
		assertThat(update.getAdditionalParameters()).isNull();
		assertThat(update.getJsonParameters()).hasSize(2).contains(other);
		final var permit = update.getJsonParameters().stream().filter(parameter -> ALCOHOL_PERMIT_KEY.equals(parameter.getKey())).findFirst().orElseThrow();
		assertThat(permit.getSchemaId()).isEqualTo(ALCOHOL_SCHEMA_ID);
		assertThat(permit.getValue()).isEqualTo(json("""
			{
			  "errandId": "granting-errand-id",
			  "caseRegistrationNumber": "ALKT-2026-000001",
			  "permitHolder": { "name": "Runt Hörnet AB" },
			  "premises": { "name": "Runt Hörnet Bar", "restaurantNumber": "22810001", "phoneNumber": "060-123456" },
			  "servingHours": [{ "from": "11:00", "to": "02:00" }],
			  "conditions": ["Ordningsvakt efter 01.00."],
			  "decision": { "caseRegistrationNumber": "ALKT-2026-000123", "decisionText": "Bifall" }
			}
			"""));
		assertThat(templateParametersCaptor.getValue())
			.containsEntry("caseNumber", ERRAND_NUMBER)
			.containsEntry("premisesName", "Runt Hörnet Bar")
			.containsEntry("premisesRestaurantNumber", "22810001")
			.containsEntry("servingHours", "11:00–02:00")
			.containsEntry("decisionText", "Bifall")
			.containsEntry("conditions", "Ordningsvakt efter 01.00.");
		assertThat(attachmentCaptor.getValue().file().getBytes()).isEqualTo(pdf);
		assertThat(attachmentCaptor.getValue().category()).isEqualTo("Tillståndsbevis");
		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
	}

	/** A change that sets no conditions leaves those of the permit in place, on the asset and on its certificate. */
	@ParameterizedTest
	@ValueSource(strings = {
		"APPROVAL", "APPROVAL_WITH_CONDITIONS"
	})
	void updateAssetKeepsTheConditionsOfThePermitWhenTheChangeSetsNone(final String outcome) {
		givenAPermitToChange(approval().outcome(outcome), """
			{ "conditions": ["Ordningsvakt efter 23.00."] }
			""", """
			{ "conditions": ["Från ändringens JSON."] }
			""");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture())).thenReturn("%PDF-1.7".getBytes());

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getJsonParameters().getFirst().getValue().path("conditions")).isEqualTo(json("""
			["Ordningsvakt efter 23.00."]
			"""));
		assertThat(templateParametersCaptor.getValue()).containsEntry("conditions", "Ordningsvakt efter 23.00.");
	}

	@Test
	void updateAssetFailsWithoutRetryOnAnApprovalWithConditionsWhenNeitherItNorThePermitHasConditions() {
		givenAPermitToChange(approval().outcome("APPROVAL_WITH_CONDITIONS"), "{}", "{}");

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("approval with conditions but has no conditions");

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	/** A rerun after the certificate failed finds the change made, and only the certificate is left to do. */
	@Test
	void updateAssetOnlyReplacesTheCertificateWhenTheAssetAlreadyHasTheChange() {
		final var changed = """
			{ "errandId": "granting-errand-id", "servingHours": [{ "from": "11:00", "to": "02:00" }], "decision": { "caseRegistrationNumber": "ALKT-2026-000123" } }
			""";
		givenAChangeOf(activeAsset().validTo(LocalDate.of(2099, 9, 30)).jsonParameters(List.of(permitParameter(changed))), approval().validTo(LocalDate.of(2099, 9, 30)), """
			{ "servingHours": [{ "from": "11:00", "to": "02:00" }] }
			""");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verify(partyAssetsIntegrationMock).replaceCertificate(eq(MUNICIPALITY_ID), eq(ASSET_ID), any());
	}

	@Test
	void updateAssetKeepsTheNewCertificateOnTheErrandOnceItHasReplacedTheOldOne() {
		final var pdf = "%PDF-1.7".getBytes();
		givenAPermitToChange(approval().created(DECISION_CREATED), "{}", "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn(pdf);

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		final InOrder inOrder = inOrder(partyAssetsIntegrationMock, supportManagementIntegrationMock);
		inOrder.verify(partyAssetsIntegrationMock).replaceCertificate(eq(MUNICIPALITY_ID), eq(ASSET_ID), any());
		inOrder.verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		inOrder.verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_COPY, pdf);
		verify(partyAssetsIntegrationMock, never()).findCertificate(any(), any());
	}

	/** A rerun after the copy went up replaces the certificate in party-assets again, but uploads no second copy. */
	@Test
	void updateAssetUploadsNoSecondCopyOfTheCertificate() {
		givenAPermitToChange(approval().created(DECISION_CREATED), "{}", "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(certificateCopy(DECISION_CREATED.plusMinutes(1))));

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		verify(partyAssetsIntegrationMock).replaceCertificate(eq(MUNICIPALITY_ID), eq(ASSET_ID), any());
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(any(), any(), any(), any(), any());
	}

	@Test
	void updateAssetPutsNoCertificateOnTheErrandThatPartyAssetsDoesNotHold() {
		givenAPermitToChange(approval(), "{}", "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).replaceCertificate(eq(MUNICIPALITY_ID), eq(ASSET_ID), any());

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isInstanceOf(ClientProblem.class);

		verify(supportManagementIntegrationMock, never()).getAttachments(any(), any(), any());
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(any(), any(), any(), any(), any());
	}

	/** A new version of the schema is taken into use with the first change after the configuration names it. */
	@Test
	void updateAssetWritesThePermitByTheConfiguredVersionOfTheSchema() {
		final var changed = """
			{ "decision": { "caseRegistrationNumber": "ALKT-2026-000123" } }
			""";
		givenAChangeOf(activeAsset().jsonParameters(List.of(new AssetJsonParameter().key(ALCOHOL_PERMIT_KEY).schemaId("2281_aot_alcohol_party_asset_0.0").value(json(changed)))),
			approval(), "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getJsonParameters()).extracting(AssetJsonParameter::getSchemaId).containsExactly(ALCOHOL_SCHEMA_ID);
	}

	/** The JSON parameter holds the permit, so the additional parameters of a permit go with its first change. */
	@Test
	void updateAssetRemovesTheAdditionalParametersOfThePermit() {
		final var changed = """
			{ "decision": { "caseRegistrationNumber": "ALKT-2026-000123" } }
			""";
		givenAChangeOf(activeAsset().additionalParameters(Map.of("errandId", "granting-errand-id")).jsonParameters(List.of(permitParameter(changed))), approval(), "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getAdditionalParameters()).isNotNull().isEmpty();
	}

	@Test
	void updateAssetEndsThePermitTodayAtTheEarliest() {
		givenAPermitToChange(approval().validTo(TODAY), "{}", "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getValidTo()).isEqualTo(TODAY);
	}

	@Test
	void updateAssetFailsWithoutRetryOnAValidToThatHasPassed() {
		final var validTo = TODAY.minusDays(1);
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().validTo(validTo)));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Decision of errand 'errand-id' is valid to %s, which has passed, so the permit is not changed".formatted(validTo));

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@Test
	void updateAssetPatchesTheAssetWhenOnlyItsValidToChanges() {
		final var changed = """
			{ "decision": { "caseRegistrationNumber": "ALKT-2026-000123" } }
			""";
		givenAChangeOf(activeAsset().validTo(LocalDate.of(2099, 9, 30)).jsonParameters(List.of(permitParameter(changed))), approval().validTo(LocalDate.of(2100, 9, 30)), "{}");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn("%PDF-1.7".getBytes());

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getValidTo()).isEqualTo(LocalDate.of(2100, 9, 30));
	}

	/**
	 * The certificate is rendered before the permit is touched, so a certificate that cannot be made leaves the permit as
	 * it was.
	 */
	@Test
	void updateAssetLeavesThePermitAloneWhenTheCertificateCannotBeRendered() {
		givenAPermitToChange(approval(), """
			{ "premises": { "name": "Runt Hörnet", "phoneNumber": "060-123456" } }
			""", """
			{ "premises": { "phoneNumber": null } }
			""");
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture()))
			.thenThrow(new NonRetryableException("Template 'serving-permit-certificate' cannot be rendered from the given parameters"));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class);

		assertThat(templateParametersCaptor.getValue()).containsEntry("premisesName", "Runt Hörnet").containsEntry("premisesPhone", "");
		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verify(partyAssetsIntegrationMock, never()).replaceCertificate(any(), any(), any());
		verify(supportManagementIntegrationMock, never()).createPdfAttachment(any(), any(), any(), any(), any());
	}

	@Test
	void updateAssetFailsWithoutRetryWhenTheChangeDecisionHasNoJsonParameter() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().jsonParameters(List.of(permitParameter("{}")))));
		when(supportManagementIntegrationMock.getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, ALCOHOL_DECISION_KEY))
			.thenThrow(new NonRetryableException("Decision 'decision-id' of errand 'errand-id' has no JSON parameter 'aot_alcohol_decision'"));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("has no JSON parameter");

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	/** A permit without the JSON parameter of its own schema has nothing for the change to go on top of. */
	@Test
	void updateAssetFailsWithoutRetryOnAPermitWithoutItsJsonParameter() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset()
			.additionalParameters(Map.of("errandId", "granting-errand-id"))
			.jsonParameters(List.of(new AssetJsonParameter().key("aot_tobacco_party_asset").value(json("{}"))))));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' has no JSON parameter 'aot_alcohol_party_asset' to change".formatted(ASSET_ID));

		verify(supportManagementIntegrationMock, never()).getDecisionJson(any(), any(), any(), any(), any());
		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void updateAssetFailsWithoutRetryOnAnAssetTypeThatIsNotChangedByADecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("An asset of type 'LowAlcoholBeerSalesNotification' is not changed by a decision");

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"", " "
	})
	void updateAssetFailsWithoutRetryWhenTheErrandNamesNoAsset(final String assetId) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(assetId == null ? null : List.of(assetId)))));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' names no asset to change");

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"asset-id", "9c8b7a6d", "9c8b7a6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d-extra"
	})
	void updateAssetFailsWithoutRetryWhenTheErrandNamesSomethingThatIsNotAnAssetId(final String assetId) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(List.of(assetId)))));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' names asset '%s', which is not an asset id".formatted(assetId));

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@Test
	void getPermitToChangeAnswersWithTheActivePermitOfTheHolder() {
		final var versioned = versioned(activeAsset().jsonParameters(List.of(permitParameter("{}"))));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned);

		assertThat(assetService.getPermitToChange(MUNICIPALITY_ID, errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(List.of(ASSET_ID)))), ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isSameAs(versioned);
		verifyNoInteractions(supportManagementIntegrationMock, templatingIntegrationMock);
	}

	/** The change goes on top of the JSON parameter, so a permit without one is refused before the case worker decides. */
	@Test
	void getPermitToChangeFailsWithoutRetryOnAPermitWithoutItsJsonParameter() {
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().additionalParameters(Map.of("errandId", "granting-errand-id"))));

		assertThatThrownBy(() -> assetService.getPermitToChange(MUNICIPALITY_ID, errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(List.of(ASSET_ID)))),
			ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' has no JSON parameter 'aot_alcohol_party_asset' to change".formatted(ASSET_ID));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"PERMIT", "LowAlcoholBeerServingNotification"
	})
	void updateAssetFailsWithoutRetryOnAnAssetOfAnotherType(final String type) {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().type(type)));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' is of type '%s', the process handles only type '%s'".formatted(ASSET_ID, type, ALCOHOL_SERVING_PERMIT.getValue()));

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void updateAssetFailsWithoutRetryWhenTheErrandHasNoParameters() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder().parameters(null));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("names no asset to change");
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = "ACTIVE", mode = EXCLUDE)
	void updateAssetFailsWithoutRetryOnAnAssetThatIsNotActive(final Status status) {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().status(status)));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' has status %s, only an active permit can be handled".formatted(ASSET_ID, status));

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void updateAssetFailsWithoutRetryOnAnAssetOfAnotherParty() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().partyId("someone-else")));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' does not belong to the permit holder of errand 'errand-id'".formatted(ASSET_ID));

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
	}

	@Test
	void updateAssetFailsWithoutRetryWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(new Errand().parameters(List.of(new Parameter().key("assetId").values(List.of(ASSET_ID)))));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no stakeholder with role '%s'".formatted(STAKEHOLDER_ROLE_PERMIT_HOLDER));

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"REJECTED", "DISMISSED", "INADMISSIBLE"
	})
	void updateAssetFailsOnAnOutcomeThatGrantsNoPermit(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().id(DECISION_ID).outcome(outcome)));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("only created or changed from one of [APPROVAL, APPROVAL_WITH_CONDITIONS]");

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void updateAssetFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no completed decision");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void updateAssetFailsOnADecisionWithoutId() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().id(null)));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no id");

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@Test
	void checkPermitAnswersWithThePermitTheErrandNames() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(List.of(ASSET_ID)))));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset()));

		assertThat(assetService.checkPermit(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void checkPermitPassesAPermitThatHasAlreadyEnded() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(List.of(ASSET_ID)))));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().status(Status.EXPIRED)));

		assertThat(assetService.checkPermit(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {
		"ACTIVE", "EXPIRED"
	}, mode = EXCLUDE)
	void checkPermitFailsWithoutRetryOnAPermitThatIsNeitherActiveNorEnded(final Status status) {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(List.of(ASSET_ID)))));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().status(status)));

		assertThatThrownBy(() -> assetService.checkPermit(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' has status %s, only an active permit can be handled".formatted(ASSET_ID, status));
	}

	@Test
	void checkPermitFailsWithoutRetryOnAnAssetOfAnotherType() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(List.of(ASSET_ID)))));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().type("LowAlcoholBeerSalesNotification")));

		assertThatThrownBy(() -> assetService.checkPermit(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' is of type 'LowAlcoholBeerSalesNotification', the process handles only type '%s'".formatted(ASSET_ID, ALCOHOL_SERVING_PERMIT.getValue()));
	}

	@Test
	void closeAssetEndsThePermitWhoseLastDayHasPassed() {
		final var lastDay = TODAY.minusDays(1);
		givenAnErrandNaming(approval().validTo(lastDay));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().validTo(TODAY.plusDays(30))));

		assertThat(assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getStatus()).isEqualTo(Status.EXPIRED);
		assertThat(updateCaptor.getValue().getValidTo()).isEqualTo(lastDay);
		assertThat(updateCaptor.getValue().getStatusReason()).isNull();
		assertThat(updateCaptor.getValue().getAdditionalParameters()).isNull();
		assertThat(updateCaptor.getValue().getJsonParameters()).isNull();
		verifyNoInteractions(templatingIntegrationMock);
		verify(partyAssetsIntegrationMock, never()).replaceCertificate(any(), any(), any());
	}

	@Test
	void closeAssetKeepsThePermitValidOnItsLastDay() {
		givenAnErrandNaming(approval().validTo(TODAY));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset()));

		assertThat(assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getStatus()).isNull();
		assertThat(updateCaptor.getValue().getValidTo()).isEqualTo(TODAY);
	}

	@Test
	void closeAssetNeverExtendsThePermit() {
		givenAnErrandNaming(approval().validTo(TODAY.plusDays(90)));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().validTo(TODAY.plusDays(30))));

		assertThat(assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
	}

	@Test
	void closeAssetSetsTheLastDayOfAPermitThatEndsLater() {
		final var lastDay = TODAY.plusDays(30);
		givenAnErrandNaming(approval().validTo(lastDay));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset()));

		assertThat(assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getStatus()).isNull();
		assertThat(updateCaptor.getValue().getValidTo()).isEqualTo(lastDay);
	}

	/** A rerun after the PATCH went through finds the last day already set. */
	@Test
	void closeAssetLeavesAPermitAlreadySetToEnd() {
		final var lastDay = TODAY.plusDays(30);
		givenAnErrandNaming(approval().validTo(lastDay));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().validTo(lastDay)));

		assertThat(assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
	}

	/** A rerun after the PATCH went through finds the permit already ended. */
	@Test
	void closeAssetLeavesAPermitThatHasAlreadyEnded() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().status(Status.EXPIRED)));

		assertThat(assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {
		"ACTIVE", "EXPIRED"
	}, mode = EXCLUDE)
	void closeAssetFailsWithoutRetryOnAnAssetThatIsNeitherActiveNorEnded(final Status status) {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().status(status)));

		assertThatThrownBy(() -> assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' has status %s, only an active permit can be handled".formatted(ASSET_ID, status));

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
	}

	@Test
	void closeAssetFailsWithoutRetryOnALastDayBeforeThePermitWasIssued() {
		givenAnErrandNaming(approval().validTo(TODAY.minusDays(10)));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().issued(TODAY.minusDays(5))));

		assertThatThrownBy(() -> assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Decision of errand 'errand-id' ends asset '%s' on %s, before it was issued on %s".formatted(ASSET_ID, TODAY.minusDays(10), TODAY.minusDays(5)));

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
	}

	@Test
	void closeAssetEndsAPermitOnTheDayItWasIssued() {
		givenAnErrandNaming(approval().validTo(TODAY.minusDays(5)));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().issued(TODAY.minusDays(5))));

		assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getValidTo()).isEqualTo(TODAY.minusDays(5));
	}

	@Test
	void closeAssetFailsWithoutRetryOnAnEndedAssetOfAnotherType() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().status(Status.EXPIRED).type("LowAlcoholBeerSalesNotification")));

		assertThatThrownBy(() -> assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("is of type 'LowAlcoholBeerSalesNotification'");
	}

	@Test
	void closeAssetFailsWithoutRetryOnAnAssetOfAnotherParty() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().partyId("someone-else")));

		assertThatThrownBy(() -> assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' does not belong to the permit holder of errand 'errand-id'".formatted(ASSET_ID));

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"REJECTED", "DISMISSED", "INADMISSIBLE"
	})
	void closeAssetFailsOnAnOutcomeThatGrantsNoPermit(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().id(DECISION_ID).outcome(outcome)));

		assertThatThrownBy(() -> assetService.closeAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT))
			.isInstanceOf(Problem.class);

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void updateAssetOfAPremisesInTimraChangesThePermitInSundsvallWithACertificateRenderedInTimra() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2262")),
			new Parameter().key("externalCaseRegistrationNumber").values(List.of("TIMRA-1")),
			new Parameter().key("assetId").values(List.of(ASSET_ID)))));
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, """
			{ "servingHours": [{ "from": "11:00", "to": "02:00" }] }
			""");
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().jsonParameters(List.of(permitParameter("{}")))));
		final var pdf = "%PDF-1.7".getBytes();
		when(templatingIntegrationMock.renderPdf(eq("2262"), eq(CERTIFICATE_TEMPLATE), any())).thenReturn(pdf);

		assertThat(assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).isEqualTo(ASSET_ID);

		verify(templatingIntegrationMock).renderPdf(eq("2262"), eq(CERTIFICATE_TEMPLATE), any());
		verify(partyAssetsIntegrationMock).getAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), any());
		verify(partyAssetsIntegrationMock).replaceCertificate(eq(MUNICIPALITY_ID), eq(ASSET_ID), any());
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, ALCOHOL_DECISION_KEY);
		verify(supportManagementIntegrationMock).getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).createPdfAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "Tillståndsbevis TIMRA-1.pdf", pdf);
		verifyNoMoreInteractions(partyAssetsIntegrationMock, templatingIntegrationMock, supportManagementIntegrationMock);
	}

	private void givenAnErrandNaming(final Decision decision) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(decision));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(
				new Parameter().key("premisesMunicipalityId").values(List.of("2281")),

				new Parameter().key("assetId").values(List.of(" ", ASSET_ID)))));
	}

	private void givenAPermitToChange(final Decision decision, final String permitJson, final String changeJson) {
		givenAChangeOf(activeAsset().jsonParameters(List.of(permitParameter(permitJson))), decision, changeJson);
	}

	private void givenAChangeOf(final Asset asset, final Decision decision, final String changeJson) {
		givenAnErrandNaming(decision);
		givenTheDecisionJson(ALCOHOL_DECISION_KEY, changeJson);
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(asset));
	}

	private void givenTheDecisionJson(final String key, final String value) {
		when(supportManagementIntegrationMock.getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, key))
			.thenReturn(json(value));
	}

	private static AssetJsonParameter permitParameter(final String value) {
		return new AssetJsonParameter().key(ALCOHOL_PERMIT_KEY).schemaId(ALCOHOL_SCHEMA_ID).value(json(value));
	}

	private static VersionedAsset versioned(final Asset asset) {
		return new VersionedAsset(asset, VERSION);
	}

	private static Asset activeAsset() {
		return new Asset().id(ASSET_ID).partyId(PARTY_ID).type(ALCOHOL_SERVING_PERMIT.getValue()).status(Status.ACTIVE);
	}

	private void givenThePermitAlreadyMadeFor(final Decision decision) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(decision));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.of(activeAsset()));
	}

	private static ErrandAttachment certificateCopy(final OffsetDateTime created) {
		return new ErrandAttachment().id("certificate-copy").fileName(CERTIFICATE_COPY).created(created);
	}

	private void givenADraftFor(final Decision decision) {
		givenNoAssetYetFor(decision);
		when(partyAssetsIntegrationMock.createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any())).thenReturn(ASSET_ID);
	}

	private void givenAPermitDraftFor(final Decision decision, final String decisionKey, final String decisionJson) {
		givenADraftFor(decision);
		givenTheDecisionJson(decisionKey, decisionJson);
	}

	private void givenNoAssetYetFor(final Decision decision) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(decision));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAsset(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());
	}

	private static Decision approval() {
		return new Decision().id(DECISION_ID).outcome("APPROVAL");
	}

	private static Errand errandWithPermitHolder() {
		return new Errand()
			.errandNumber(ERRAND_NUMBER)
			.parameters(List.of(new Parameter().key("premisesMunicipalityId").values(List.of("2281"))))
			.stakeholders(List.of(new Stakeholder().role("APPLICANT").externalId("applicant-id"), new Stakeholder().role("PRIMARY").externalId(PARTY_ID)));
	}

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}
}
