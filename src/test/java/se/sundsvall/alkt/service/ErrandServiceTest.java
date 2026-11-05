package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.CaseNumberCheck;
import se.sundsvall.dept44.exception.ClientProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.service.model.CaseNumberCheck.MISSING;
import static se.sundsvall.alkt.service.model.CaseNumberCheck.PRESENT;

@ExtendWith(MockitoExtension.class)
class ErrandServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	// Days after the step was reached, as on a retry after an incident.
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneId.of("Europe/Stockholm"));
	// Past midnight in Sweden but not in UTC, so the day must be the Swedish one.
	private static final Instant REACHED_AT = Instant.parse("2026-10-19T22:30:00Z");
	private static final Parameter IN_TIMRA = new Parameter().key("premisesMunicipalityId").values(List.of("2262"));

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	private ErrandService errandService;

	@BeforeEach
	void setUp() {
		errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
	}

	@AfterEach
	void verifyTheErrandIsReadOnce() {
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void setCompleteAtWritesTheDayTheStepWasReachedBesideTheOtherParametersOfTheErrand() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand()
			.version(7L)
			.parameters(List.of(new Parameter().key("premisesName").values(List.of("Runt Hörnet")))));

		final var completeAt = errandService.setCompleteAt(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, REACHED_AT);

		assertThat(completeAt).isEqualTo(LocalDate.of(2026, 10, 20));
		verify(supportManagementIntegrationMock).updateErrandParameters(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, 7L, List.of(
			new Parameter().key("premisesName").values(List.of("Runt Hörnet")),
			new Parameter().key("completeAt").values(List.of("2026-10-20"))));
	}

	/** Only a value in externalCaseRegistrationNumber counts, and a blank one does not. */
	private static Stream<Arguments> caseNumbersInTimra() {
		return Stream.of(
			Arguments.of(new Parameter().key("externalCaseRegistrationNumber").values(List.of("TSN 2026/0153")), PRESENT),
			Arguments.of(new Parameter().key("externalCaseRegistrationNumber").values(List.of("", "TSN 2026/0153")), PRESENT),
			Arguments.of(new Parameter().key("externalCaseRegistrationNumber").values(List.of("  ")), MISSING),
			Arguments.of(new Parameter().key("externalCaseRegistrationNumber").values(null), MISSING),
			Arguments.of(new Parameter().key("premisesName").values(List.of("Runt Hörnet")), MISSING),
			Arguments.of(new Parameter().key("public360CaseNumber").values(List.of("KS 2026/0042")), MISSING));
	}

	@ParameterizedTest
	@MethodSource("caseNumbersInTimra")
	void anErrandInTimraHasTheCaseNumberItNeedsOnlyInExternalCaseRegistrationNumber(final Parameter parameter, final CaseNumberCheck expected) {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand()
			.errandNumber("ALKT-2026-000123")
			.parameters(List.of(IN_TIMRA, parameter)));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(expected);
	}

	@Test
	void anErrandInSundsvallNeedsNoCaseNumber() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2281")))));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.NOT_NEEDED);
	}

	@Test
	void anErrandWithoutPremisesMunicipalityStopsTheCaseNumberCheck() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().id(ERRAND_ID).parameters(List.of(
			new Parameter().key("externalCaseRegistrationNumber").values(List.of("TSN 2026/0153")))));

		assertThatThrownBy(() -> errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no premisesMunicipalityId");
	}

	@Test
	void anErrandThatCannotBeReadFailsTheCheckToBeRetried() {
		final var problem = new ClientProblem(BAD_GATEWAY, "Support Management is down");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(problem);

		assertThatThrownBy(() -> errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isSameAs(problem);
	}

	@Test
	void anErrandInTimraRequiresACaseNumber() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(IN_TIMRA)));

		assertThat(errandService.isCaseNumberRequired(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isTrue();
	}

	@Test
	void anErrandInSundsvallRequiresNoCaseNumber() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2281")))));

		assertThat(errandService.isCaseNumberRequired(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isFalse();
	}

	@Test
	void anErrandWithABlankPremisesMunicipalityStopsTheRequirement() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().id(ERRAND_ID).parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of(" ")))));

		assertThatThrownBy(() -> errandService.isCaseNumberRequired(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no premisesMunicipalityId");
	}

	@Test
	void theRequirementOfAnErrandThatCannotBeReadFailsToBeRetried() {
		final var problem = new ClientProblem(BAD_GATEWAY, "Support Management is down");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(problem);

		assertThatThrownBy(() -> errandService.isCaseNumberRequired(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isSameAs(problem);
	}
}
