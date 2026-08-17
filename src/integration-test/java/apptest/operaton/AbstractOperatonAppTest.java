package apptest.operaton;

import apptest.AbstractEngineAppTest;
import apptest.engine.EngineTestProperties;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for the Operaton-engine integration tests: points every test in this package at a live Operaton engine, so the
 * worker write-path executes against the Operaton REST API.
 */
abstract class AbstractOperatonAppTest extends AbstractEngineAppTest {

	@DynamicPropertySource
	static void engine(DynamicPropertyRegistry registry) {
		EngineTestProperties.registerOperaton(registry);
	}
}
