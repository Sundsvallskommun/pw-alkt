package apptest.engine;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * JVM-wide singleton Operaton container, shared across all integration tests so the engine image starts at most once
 * per test run. The container is started lazily on first use (see {@link #operatonBaseUrl}) and reaped by
 * Testcontainers' Ryuk on JVM exit - there is intentionally no per-class teardown so the same engine is reused by every
 * scenario.
 */
public final class EngineContainers {

	/*
	 * Pinned rather than :latest - an unpinned tag makes the build non-reproducible and lets an upstream release break
	 * CI without a change in this repository. Bump deliberately.
	 */
	private static final String OPERATON_IMAGE = "operaton/operaton:2.1.3";

	// "/" answers 302, so wait on the REST engine endpoint which answers 200 once the engine is up.
	@SuppressWarnings("resource")
	private static final GenericContainer<?> OPERATON = new GenericContainer<>(OPERATON_IMAGE)
		.waitingFor(Wait.forHttp("/engine-rest/engine").forStatusCode(200))
		.withExposedPorts(8080);

	private EngineContainers() {}

	/**
	 * Starts the Operaton container if it is not already running and returns its {@code /engine-rest} base URL.
	 */
	public static synchronized String operatonBaseUrl() {
		if (!OPERATON.isRunning()) {
			OPERATON.start();
		}
		return "http://localhost:" + OPERATON.getMappedPort(8080) + "/engine-rest";
	}
}
