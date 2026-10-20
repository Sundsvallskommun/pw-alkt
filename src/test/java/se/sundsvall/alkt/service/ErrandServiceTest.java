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
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrandServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	// Past midnight in Sweden but not in UTC, so the day must be the Swedish one.
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T22:30:00Z"), ZoneId.of("Europe/Stockholm"));

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Test
	void setCompleteAtWritesTodayBesideTheOtherParametersOfTheErrand() {
		final var errandService = new ErrandService(supportManagementIntegrationMock, CLOCK);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand()
			.version(7L)
			.parameters(List.of(new Parameter().key("premisesName").values(List.of("Runt Hörnet")))));

		final var completeAt = errandService.setCompleteAt(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);

		assertThat(completeAt).isEqualTo(LocalDate.of(2026, 10, 20));
		verify(supportManagementIntegrationMock).updateErrandParameters(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, 7L, List.of(
			new Parameter().key("premisesName").values(List.of("Runt Hörnet")),
			new Parameter().key("completeAt").values(List.of("2026-10-20"))));
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}
}
