package se.sundsvall.alkt.integration.employee;

import generated.se.sundsvall.employee.PortalPersonData;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static java.util.stream.Collectors.joining;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class EmployeeIntegration {

	private static final String DOMAIN = "personal";

	private final EmployeeClient employeeClient;

	EmployeeIntegration(final EmployeeClient employeeClient) {
		this.employeeClient = employeeClient;
	}

	public String getName(final String municipalityId, final String loginName) {
		final var person = getPortalPersonData(municipalityId, loginName);
		return Stream.of(person.getGivenname(), person.getLastname()).filter(StringUtils::isNotBlank).collect(joining(" "));
	}

	private PortalPersonData getPortalPersonData(final String municipalityId, final String loginName) {
		try {
			return employeeClient.getPortalPersonData(municipalityId, DOMAIN, loginName);
		} catch (final ClientProblem e) {
			if (!NOT_FOUND.equals(e.getStatus())) {
				throw e;
			}
			throw Problem.builder()
				.withStatus(NOT_FOUND)
				.withTitle(NOT_FOUND.getReasonPhrase())
				.withDetail("The employee register does not know the case worker the errand is assigned to")
				.withCause(e)
				.build();
		}
	}
}
