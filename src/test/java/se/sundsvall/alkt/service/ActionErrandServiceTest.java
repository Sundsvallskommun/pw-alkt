package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Classification;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.configuration.ActionErrandProperties;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.ActionErrandResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionErrandServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "inspection-id";
	private static final String REFERRED_FROM = "LINK|inspection-id;case;supportmanagement;ALKT|";
	private static final String LABEL_ID = "label-id";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Captor
	private ArgumentCaptor<Errand> errandCaptor;

	private ActionErrandService service;

	@BeforeEach
	void setUp() {
		service = new ActionErrandService(supportManagementIntegrationMock, new ActionErrandProperties(LABEL_ID));
	}

	@AfterEach
	void verifyNoUnexpectedInteractions() {
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void createsTheActionErrandWithTheLabelAndARelationToTheInspection() {
		final var classification = new Classification().category("AOT").type("AOT.UNCATEGORIZED");
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(new Errand().id(ERRAND_ID).title("Brister vid tillsyn").assignedUserId("handlaggare").classification(classification)
				.stakeholders(List.of(new Stakeholder().role("PRIMARY").externalId("party-id"))).measures(List.of(new Measure().type("DEFICIENCY").status("ACTIVE"))));
		when(supportManagementIntegrationMock.createErrand(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(REFERRED_FROM), errandCaptor.capture())).thenReturn("action-errand-id");

		assertThat(service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(ActionErrandResult.created("action-errand-id"));

		assertThat(errandCaptor.getValue().getTitle()).isEqualTo("Åtgärdsärende");
		assertThat(errandCaptor.getValue().getReporterUserId()).isEqualTo("handlaggare");
		assertThat(errandCaptor.getValue().getClassification()).isEqualTo(classification);
		assertThat(errandCaptor.getValue().getLabels()).containsExactly(new ErrandLabel().id(LABEL_ID));
		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).createErrand(MUNICIPALITY_ID, NAMESPACE, REFERRED_FROM, errandCaptor.getValue());
	}

	@Test
	void answersWithTheActionErrandAnEarlierAttemptCreated() {
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.of("action-errand-id"));

		assertThat(service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(ActionErrandResult.created("action-errand-id"));

		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
	}

	@Test
	void failsWithoutRetryForAnInspectionWithoutPermitHolder() {
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(new Errand().id(ERRAND_ID).stakeholders(List.of(new Stakeholder().role("CONTACT").externalId("contact-id"), new Stakeholder().role("PRIMARY"))).measures(List.of(new Measure().type("DEFICIENCY").status("ACTIVE"))));

		assertThatThrownBy(() -> service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'inspection-id' has no stakeholder with role 'PRIMARY'");

		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void refusesAnInspectionWithoutAssignedUser(final String assignedUserId) {
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(new Errand().id(ERRAND_ID).assignedUserId(assignedUserId).stakeholders(List.of(new Stakeholder().role("PRIMARY").externalId("party-id")))
				.measures(List.of(new Measure().type("DEFICIENCY").status("ACTIVE"))));

		assertThat(service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isEqualTo(ActionErrandResult.refused("Errand 'inspection-id' has no assigned user to report the action errand, no action errand created"));

		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void refusesAnInspectionWithoutDeficiencies(final List<Measure> measures) {
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().id(ERRAND_ID).measures(measures));

		assertThat(service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(ActionErrandResult.refused("Errand 'inspection-id' has no deficiencies, no action errand created"));

		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"DRAFT", "COMPLETED", "CANCELLED"
	})
	void refusesAnInspectionWithoutActiveMeasures(final String status) {
		when(supportManagementIntegrationMock.findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID)).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(new Errand().id(ERRAND_ID).stakeholders(List.of(new Stakeholder().role("PRIMARY").externalId("party-id"))).measures(List.of(new Measure().type("DEFICIENCY").status(status))));

		assertThat(service.createActionErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(ActionErrandResult.refused("Errand 'inspection-id' has no deficiencies, no action errand created"));

		verify(supportManagementIntegrationMock).findErrandIdByExternalTag(MUNICIPALITY_ID, NAMESPACE, "inspectionErrandId", ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}
}
