package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Measure;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionErrandServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "inspection-id";
	private static final String REFERRED_FROM = "LINK|inspection-id;errand;support-management;ALKT|";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Captor
	private ArgumentCaptor<Errand> errandCaptor;

	@InjectMocks
	private ActionErrandService service;

	@AfterEach
	void verifyNoUnexpectedInteractions() {
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void createsTheActionErrandWithARelationToTheInspection() {
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(new Errand().id(ERRAND_ID).title("Brister vid tillsyn").measures(List.of(new Measure().type("DEFICIENCY"))));
		when(supportManagementIntegrationMock.createErrand(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(REFERRED_FROM), errandCaptor.capture())).thenReturn("action-errand-id");

		assertThat(service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "INSPECTION", "ACTION_ERRAND")).contains("action-errand-id");

		assertThat(errandCaptor.getValue().getTitle()).isEqualTo("Brister vid tillsyn");
		assertThat(errandCaptor.getValue().getClassification().getType()).isEqualTo("ACTION_ERRAND");
		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).createErrand(MUNICIPALITY_ID, NAMESPACE, REFERRED_FROM, errandCaptor.getValue());
	}

	@Test
	void answersWithTheActionErrandAnEarlierAttemptCreated() {
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.of("action-errand-id"));

		assertThat(service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "INSPECTION", "ACTION_ERRAND")).contains("action-errand-id");

		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void createsNothingForAnInspectionWithoutDeficiencies(final List<Measure> measures) {
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().id(ERRAND_ID).measures(measures));

		assertThat(service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "INSPECTION", "ACTION_ERRAND")).isEmpty();

		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@ParameterizedTest
	@CsvSource(value = {
		"null, ACTION_ERRAND",
		"INSPECTION, null",
		"INSPECTION, ' '"
	}, nullValues = "null")
	void failsWithoutRetryWhenTheStepLacksItsInputParameters(final String category, final String type) {
		assertThatThrownBy(() -> service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, category, type))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The step has no category or type for the action errand, see the input parameters in the bpmn schema");

		verifyNoInteractions(supportManagementIntegrationMock);
	}
}
