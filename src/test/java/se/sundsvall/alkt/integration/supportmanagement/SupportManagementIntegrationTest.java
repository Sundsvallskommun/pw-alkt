package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Errand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.dept44.exception.ClientProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupportManagementIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "b82bd8ac-1507-4d9a-958d-369261eecc15";

	@Mock
	private SupportManagementClient supportManagementClientMock;

	@InjectMocks
	private SupportManagementIntegration supportManagementIntegration;

	@Test
	void patchesTheErrandOnThePathFromItsArguments() {
		supportManagementIntegration.patchProcessState(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ProcessStateReport.completed());

		verify(supportManagementClientMock).patchErrand(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), isNull(), eq(false), any());
	}

	@Test
	void patchesNoFieldsOnTheErrandYet() {
		supportManagementIntegration.patchProcessState(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ProcessStateReport.completed());

		final var bodyCaptor = ArgumentCaptor.forClass(Errand.class);
		verify(supportManagementClientMock).patchErrand(any(), any(), any(), any(), any(), bodyCaptor.capture());
		assertThat(bodyCaptor.getValue()).hasAllNullFieldsOrProperties();
	}

	@Test
	void sendsNoIfMatchWhenTheReportCarriesNoVersion() {
		supportManagementIntegration.patchProcessState(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ProcessStateReport.completed());

		verify(supportManagementClientMock).patchErrand(any(), any(), any(), isNull(), any(), any());
	}

	@Test
	void sendsTheReadVersionAsAQuotedIfMatch() {
		supportManagementIntegration.patchProcessState(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ProcessStateReport.completed().withErrandVersion(7L));

		verify(supportManagementClientMock).patchErrand(any(), any(), any(), eq("\"7\""), any(), any());
	}

	@Test
	void neverTriggersAProcessOnTheReportedErrand() {
		supportManagementIntegration.patchProcessState(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ProcessStateReport.completed());

		verify(supportManagementClientMock).patchErrand(any(), any(), any(), any(), eq(false), any());
	}

	@Test
	void letsAFailedPatchPropagate() {
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Precondition Failed")).when(supportManagementClientMock)
			.patchErrand(any(), any(), any(), any(), any(), any());

		assertThatThrownBy(() -> supportManagementIntegration.patchProcessState(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ProcessStateReport.completed()))
			.isInstanceOf(ClientProblem.class);
	}
}
