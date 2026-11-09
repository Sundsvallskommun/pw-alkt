package se.sundsvall.alkt.integration.employee;

import generated.se.sundsvall.employee.PortalPersonData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class EmployeeIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private EmployeeClient employeeClientMock;

	@InjectMocks
	private EmployeeIntegration employeeIntegration;

	@Test
	void getNameJoinsGivenNameAndLastName() {
		when(employeeClientMock.getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin"))
			.thenReturn(new PortalPersonData().givenname("Erik").lastname("Lindqvist").fullname("Lindqvist Erik"));

		assertThat(employeeIntegration.getName(MUNICIPALITY_ID, "er12lin")).isEqualTo("Erik Lindqvist");

		verify(employeeClientMock).getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin");
	}

	@Test
	void getNameLeavesOutABlankGivenName() {
		when(employeeClientMock.getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin"))
			.thenReturn(new PortalPersonData().givenname(" ").lastname("Lindqvist"));

		assertThat(employeeIntegration.getName(MUNICIPALITY_ID, "er12lin")).isEqualTo("Lindqvist");

		verify(employeeClientMock).getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin");
	}

	@Test
	void getNameFailsWithAProblemOfItsOwnForAnAccountTheRegisterDoesNotKnow() {
		final var notFound = new ClientProblem(NOT_FOUND, "employee error: {status=404 Not Found, title=Not Found}");
		when(employeeClientMock.getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin")).thenThrow(notFound);

		assertThatThrownBy(() -> employeeIntegration.getName(MUNICIPALITY_ID, "er12lin"))
			.isNotInstanceOf(ClientProblem.class)
			.hasCause(notFound)
			.isInstanceOfSatisfying(Problem.class, problem -> {
				assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
				assertThat(problem.getDetail()).isEqualTo("The employee register does not know the case worker the errand is assigned to");
			});

		verify(employeeClientMock).getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin");
	}

	@Test
	void getNamePassesOnAnyOtherClientProblem() {
		final var forbidden = new ClientProblem(BAD_GATEWAY, "employee error: {status=403 Forbidden, title=Forbidden}");
		when(employeeClientMock.getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin")).thenThrow(forbidden);

		assertThatThrownBy(() -> employeeIntegration.getName(MUNICIPALITY_ID, "er12lin")).isSameAs(forbidden);

		verify(employeeClientMock).getPortalPersonData(MUNICIPALITY_ID, "personal", "er12lin");
	}
}
