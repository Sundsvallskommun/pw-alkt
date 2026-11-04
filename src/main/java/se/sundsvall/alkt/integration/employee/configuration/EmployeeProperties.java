package se.sundsvall.alkt.integration.employee.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.employee")
public record EmployeeProperties(int connectTimeout, int readTimeout) {
}
