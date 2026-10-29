package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

@ExtendWith(MockitoExtension.class)
class ErrandServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	// Days after the step was reached, as on a retry after an incident.
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneId.of("Europe/Stockholm"));
	// Past midnight in Sweden but not in UTC, so the day must be the Swedish one.
	private static final Instant REACHED_AT = Instant.parse("2026-10-19T22:30:00Z");

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Test
	void setCompleteAtWritesTheDayTheStepWasReachedBesideTheOtherParametersOfTheErrand() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand()
			.version(7L)
			.parameters(List.of(new Parameter().key("premisesName").values(List.of("Runt Hörnet")))));

		final var completeAt = errandService.setCompleteAt(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, REACHED_AT);

		assertThat(completeAt).isEqualTo(LocalDate.of(2026, 10, 20));
		verify(supportManagementIntegrationMock).updateErrandParameters(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, 7L, List.of(
			new Parameter().key("premisesName").values(List.of("Runt Hörnet")),
			new Parameter().key("completeAt").values(List.of("2026-10-20"))));
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void anErrandInTimraWithACaseNumberHasTheCaseNumberItNeeds() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2262")),
			new Parameter().key("timraCaseNumber").values(List.of("TSN 2026/0153")))));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.PRESENT);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void anErrandInTimraWithoutACaseNumberLacksTheCaseNumberItNeeds() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2262")),
			new Parameter().key("premisesName").values(List.of("Runt Hörnet")))));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.MISSING);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void anErrandInTimraWithABlankCaseNumberLacksTheCaseNumberItNeeds() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2262")),
			new Parameter().key("timraCaseNumber").values(List.of("  ")))));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.MISSING);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void anErrandInTimraWithACaseNumberParameterWithoutValuesLacksTheCaseNumberItNeeds() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2262")),
			new Parameter().key("timraCaseNumber").values(null))));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.MISSING);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void anErrandInTimraWithAnEmptyAndAFilledCaseNumberValueHasTheCaseNumberItNeeds() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2262")),
			new Parameter().key("timraCaseNumber").values(List.of("", "TSN 2026/0153")))));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.PRESENT);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void aCaseNumberOfAnotherParameterDoesNotCountInTimra() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand()
			.errandNumber("ALKT-2026-000123")
			.parameters(List.of(
				new Parameter().key("premisesMunicipalityId").values(List.of("2262")),
				new Parameter().key("public360CaseNumber").values(List.of("KS 2026/0042")))));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.MISSING);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void anErrandInSundsvallNeedsNoCaseNumber() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2281")))));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.NOT_NEEDED);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void anErrandWithoutPremisesMunicipalityIsInSundsvallAndNeedsNoCaseNumber() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(null));

		assertThat(errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(CaseNumberCheck.NOT_NEEDED);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void anErrandThatCannotBeReadFailsTheCheckToBeRetried() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		final var problem = new ClientProblem(BAD_GATEWAY, "Support Management is down");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(problem);

		assertThatThrownBy(() -> errandService.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isSameAs(problem)
			.isNotInstanceOf(NonRetryableException.class);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void premisesMunicipalityIdIsTimraForAnErrandInTimra() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(List.of(
			new Parameter().key("premisesMunicipalityId").values(List.of("2262")))));

		assertThat(errandService.getPremisesMunicipalityId(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("2262");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void premisesMunicipalityIdIsSundsvallForAnErrandWithoutIt() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().parameters(null));

		assertThat(errandService.getPremisesMunicipalityId(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("2281");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void premisesMunicipalityIdOfAnErrandThatCannotBeReadFailsToBeRetried() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		final var problem = new ClientProblem(BAD_GATEWAY, "Support Management is down");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(problem);

		assertThatThrownBy(() -> errandService.getPremisesMunicipalityId(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isSameAs(problem);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}
}
