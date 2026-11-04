package se.sundsvall.alkt.integration.employee;

import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import static java.util.stream.Collectors.joining;

@Component
public class EmployeeIntegration {

	private static final String DOMAIN = "personal";

	private final EmployeeClient employeeClient;

	EmployeeIntegration(final EmployeeClient employeeClient) {
		this.employeeClient = employeeClient;
	}

	public String findName(final String municipalityId, final String loginName) {
		return employeeClient.getPortalPersonData(municipalityId, DOMAIN, loginName)
			.map(person -> Stream.of(person.getGivenname(), person.getLastname()).filter(StringUtils::isNotBlank).collect(joining(" ")))
			.orElse("");
	}
}
