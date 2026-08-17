package apptest.engine;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Points the application's engine-facing properties at a live engine container for an integration test. Each test leaf
 * picks the engine by delegating its {@code @DynamicPropertySource} to this class.
 * <p>
 * Both URLs target the same container: the external task client poll URL ({@code camunda.bpm.client.base-url}) and the
 * Operaton Feign client ({@code integration.operaton.url}, which the test helpers also use to read process history), so
 * the poll path and the write path cannot drift apart.
 */
public final class EngineTestProperties {

	private EngineTestProperties() {}

	public static void registerOperaton(DynamicPropertyRegistry registry) {
		final var engineBaseUrl = EngineContainers.baseUrl(EngineContainers.OPERATON);

		registry.add("integration.operaton.url", () -> engineBaseUrl);
		registry.add("camunda.bpm.client.base-url", () -> engineBaseUrl);
	}
}
