package se.sundsvall.alkt.integration.operaton.deployment;

import feign.form.FormData;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.alkt.integration.operaton.deployment.DeploymentProperties.ProcessArchive;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.DigestUtils.md5DigestAsHex;
import static se.sundsvall.dept44.util.ResourceUtils.requireNotBlank;

/**
 * Deploys every process resource matched by the configured patterns to the tenant that owns it, at startup. All process
 * definitions of this service (ansokan, and later anmalan and tillsyn) are picked up by the same resource pattern, so
 * adding a schema to {@code processmodels/} is enough to have it deployed.
 */
@Configuration
public class TenantAwareAutoDeployment {

	private static final String DEFAULT_PATTERN_PREFIX = "classpath*:**/*.";
	private static final String FILETYPE_BPMN = "bpmn";
	private static final String FILETYPE_DMN = "dmn";
	private static final String FILETYPE_FORM = "form";
	private static final Resource[] NO_RESOURCES = {};
	private static final String DEPLOYMENT_CONTENT_TYPE = "application/octet-stream";

	private final OperatonClient operatonClient;
	private final DeploymentProperties deployments;
	private final ResourcePatternResolver patternResolver;

	TenantAwareAutoDeployment(final OperatonClient operatonClient, final DeploymentProperties deployments, final ResourcePatternResolver patternResolver) {
		this.operatonClient = operatonClient;
		this.deployments = deployments;
		this.patternResolver = patternResolver;
	}

	@PostConstruct
	public void deployProcessResources() {
		if (isNull(deployments) || !deployments.isAutoDeployEnabled()) {
			return;
		}

		ofNullable(deployments.getProcesses()).orElse(emptyList()).forEach(processArchive -> {
			deployResources(processArchive, getResources(isNull(processArchive.bpmnResourcePattern()) ? DEFAULT_PATTERN_PREFIX + FILETYPE_BPMN : processArchive.bpmnResourcePattern()), FILETYPE_BPMN);
			deployResources(processArchive, getResources(isNull(processArchive.dmnResourcePattern()) ? DEFAULT_PATTERN_PREFIX + FILETYPE_DMN : processArchive.dmnResourcePattern()), FILETYPE_DMN);
			deployResources(processArchive, getResources(isNull(processArchive.formResourcePattern()) ? DEFAULT_PATTERN_PREFIX + FILETYPE_FORM : processArchive.formResourcePattern()), FILETYPE_FORM);
		});
	}

	private void deployResources(final ProcessArchive processArchive, final List<Resource> resourcesToDeploy, final String type) {
		// Validate that name is present
		requireNotBlank(processArchive.name(), "Processname must be set");

		for (final var processResource : resourcesToDeploy) {
			try {
				/*
				 * The resource is read through an InputStream so that deployment also works from a jar-packed environment, and
				 * is handed to the client as in memory form data. The file name has to carry the correct extension, since that
				 * is what the deployer uses to recognize the resource as e.g. a BPMN file.
				 */
				final var content = readContent(processResource);

				operatonClient.deploy(
					processArchive.tenant(), // tenantId
					processResource.getFilename(),
					true, // changedOnly
					true, // duplicateFiltering
					processArchive.name() + " (" + processArchive.tenant() + ") - " + processResource.getFilename(), // deploymentName
					null,
					new FormData(DEPLOYMENT_CONTENT_TYPE, getResourceFilename(processResource, type), content));
			} catch (final Exception e) {
				throw new DeploymentException(e);
			}
		}
	}

	private byte[] readContent(Resource processResource) throws IOException {
		try (var inputStream = processResource.getInputStream()) {
			return inputStream.readAllBytes();
		}
	}

	private List<Resource> getResources(final String path) {
		try {
			return Arrays.asList(ofNullable(patternResolver.getResources(path)).orElse(NO_RESOURCES));
		} catch (final IOException e) {
			throw new DeploymentException(e);
		}
	}

	private String getResourceFilename(final Resource processResource, final String type) throws IOException {
		if (processResource.getFilename() != null) {
			return processResource.getFilename();
		}

		return md5DigestAsHex(processResource.getInputStream()) + '.' + type;
	}
}
