package apptest.engine;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Points the application's engine-facing properties at a live Operaton container for an integration test. Each test
 * leaf wires this up through its {@code @DynamicPropertySource}.
 * <p>
 * Both URLs target the same container: the external task client poll URL ({@code camunda.bpm.client.base-url}) and the
 * Operaton Feign client ({@code integration.operaton.url}, which the test helpers also use to read process history), so
 * the poll path and the write path cannot drift apart.
 */
public final class EngineTestProperties {

	private EngineTestProperties() {}

	public static void registerOperaton(DynamicPropertyRegistry registry) {
		final var engineBaseUrl = EngineContainers.operatonBaseUrl();

		registry.add("integration.operaton.url", () -> engineBaseUrl);
		registry.add("camunda.bpm.client.base-url", () -> engineBaseUrl);
	}
}
