package se.sundsvall.alkt.integration.employee;

import generated.se.sundsvall.employee.PortalPersonData;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private EmployeeClient employeeClientMock;

	@InjectMocks
	private EmployeeIntegration employeeIntegration;

	@Test
	void findNameJoinsGivenNameAndLastName() {
		when(employeeClientMock.getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin"))
			.thenReturn(Optional.of(new PortalPersonData().givenname("Erik").lastname("Lindqvist").fullname("Lindqvist Erik")));

		assertThat(employeeIntegration.findName(MUNICIPALITY_ID, "er12lin")).isEqualTo("Erik Lindqvist");

		verify(employeeClientMock).getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin");
	}

	@Test
	void findNameLeavesOutABlankGivenName() {
		when(employeeClientMock.getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin"))
			.thenReturn(Optional.of(new PortalPersonData().givenname(" ").lastname("Lindqvist")));

		assertThat(employeeIntegration.findName(MUNICIPALITY_ID, "er12lin")).isEqualTo("Lindqvist");

		verify(employeeClientMock).getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin");
	}

	@Test
	void findNameIsEmptyForAnAccountTheRegisterDoesNotKnow() {
		when(employeeClientMock.getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin")).thenReturn(Optional.empty());

		assertThat(employeeIntegration.findName(MUNICIPALITY_ID, "er12lin")).isEmpty();

		verify(employeeClientMock).getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin");
	}
}
