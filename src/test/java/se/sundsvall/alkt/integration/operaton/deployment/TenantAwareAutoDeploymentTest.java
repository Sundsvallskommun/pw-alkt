package se.sundsvall.alkt.integration.operaton.deployment;

import feign.form.FormData;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.alkt.integration.operaton.deployment.DeploymentProperties.ProcessArchive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAwareAutoDeploymentTest {

	private static final String PROCESSMODEL_PATH = "processmodels/";
	private static final String PROCESSMODEL_FILE = "alkt-ansokan.bpmn";

	private static final String DEFAULT_PATTERN_PREFIX = "classpath*:**/*.";
	private static final String FILETYPE_BPMN = "bpmn";
	private static final String FILETYPE_DMN = "dmn";
	private static final String FILETYPE_FORM = "form";

	@Mock
	private OperatonClient operatonClientMock;

	@Mock
	private DeploymentProperties deploymentPropertiesMock;

	@Mock
	private ProcessArchive processArchiveMock;

	@Mock
	private ResourcePatternResolver resourcePatternResolverMock;

	@Mock
	private Resource resourceMock;

	@InjectMocks
	private TenantAwareAutoDeployment tenantAwareAutoDeployment;

	@Captor
	private ArgumentCaptor<FormData> formDataCaptor;

	@Test
	void autoDeployDisabled() {

		when(deploymentPropertiesMock.isAutoDeployEnabled()).thenReturn(false);

		tenantAwareAutoDeployment.deployProcessResources();

		verify(deploymentPropertiesMock, atLeastOnce()).isAutoDeployEnabled();
		verifyNoInteractions(operatonClientMock, resourcePatternResolverMock);
	}

	@Test
	void autoDeployEnabledButNoDefinedProcesses() {
		when(deploymentPropertiesMock.isAutoDeployEnabled()).thenReturn(true);

		tenantAwareAutoDeployment.deployProcessResources();

		verify(deploymentPropertiesMock, atLeastOnce()).isAutoDeployEnabled();
		verifyNoInteractions(operatonClientMock, resourcePatternResolverMock);
	}

	@Test
	void autoDeployEnabledWithDefinedProcessAndDefaultPathPatterns() throws IOException {
		final var name = "name";

		when(deploymentPropertiesMock.isAutoDeployEnabled()).thenReturn(true);
		when(deploymentPropertiesMock.getProcesses()).thenReturn(List.of(processArchiveMock));
		when(processArchiveMock.name()).thenReturn(name);

		tenantAwareAutoDeployment.deployProcessResources();

		verify(deploymentPropertiesMock, atLeastOnce()).isAutoDeployEnabled();
		verify(resourcePatternResolverMock).getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_BPMN);
		verify(resourcePatternResolverMock).getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_DMN);
		verify(resourcePatternResolverMock).getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_FORM);
		verifyNoMoreInteractions(resourcePatternResolverMock);
		verifyNoInteractions(operatonClientMock);
	}

	@Test
	void autoDeployEnabledWithDefinedProcessAndDefaultPathPatternsButNoNameSet() {
		when(deploymentPropertiesMock.isAutoDeployEnabled()).thenReturn(true);
		when(deploymentPropertiesMock.getProcesses()).thenReturn(List.of(processArchiveMock));

		final var exception = assertThrows(IllegalArgumentException.class, () -> tenantAwareAutoDeployment.deployProcessResources());

		verifyNoInteractions(operatonClientMock);
		assertThat(exception.getMessage()).isEqualTo("Processname must be set");
	}

	@Test
	void autoDeployEnabledWithDefinedProcessAndCustomPathPatterns() throws IOException {
		final var name = "name";
		final var customPatternBpmn = "classpath*:**/custompath/*.custom_bpmn";
		final var customPatternDmn = "classpath*:**/custompath/*..custom_dmn";
		final var customPatternForm = "classpath*:**/custompath/*..custom_form";

		when(deploymentPropertiesMock.isAutoDeployEnabled()).thenReturn(true);
		when(deploymentPropertiesMock.getProcesses()).thenReturn(List.of(processArchiveMock));
		when(processArchiveMock.name()).thenReturn(name);
		when(processArchiveMock.bpmnResourcePattern()).thenReturn(customPatternBpmn);
		when(processArchiveMock.dmnResourcePattern()).thenReturn(customPatternDmn);
		when(processArchiveMock.formResourcePattern()).thenReturn(customPatternForm);

		tenantAwareAutoDeployment.deployProcessResources();

		verify(deploymentPropertiesMock, atLeastOnce()).isAutoDeployEnabled();
		verify(resourcePatternResolverMock).getResources(customPatternBpmn);
		verify(resourcePatternResolverMock).getResources(customPatternDmn);
		verify(resourcePatternResolverMock).getResources(customPatternForm);
		verifyNoMoreInteractions(resourcePatternResolverMock);
		verifyNoInteractions(operatonClientMock);
	}

	@Test
	void autoDeployEnabledWithDefinedProcessAndMatchingDeploymentResources() throws IOException {
		final var name = "name";
		final var tenant = "tenant";
		final var deploymentName = name + " (" + tenant + ") - " + PROCESSMODEL_FILE;
		final var processFile = new ClassPathResource(PROCESSMODEL_PATH + PROCESSMODEL_FILE);

		when(deploymentPropertiesMock.isAutoDeployEnabled()).thenReturn(true);
		when(deploymentPropertiesMock.getProcesses()).thenReturn(List.of(processArchiveMock));
		when(processArchiveMock.tenant()).thenReturn(tenant);
		when(processArchiveMock.name()).thenReturn(name);
		when(resourcePatternResolverMock.getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_BPMN)).thenReturn(new Resource[] {
			resourceMock
		});
		when(resourceMock.getFilename()).thenReturn(PROCESSMODEL_FILE);
		when(resourceMock.getInputStream()).thenReturn(processFile.getInputStream());

		tenantAwareAutoDeployment.deployProcessResources();

		verify(deploymentPropertiesMock, atLeastOnce()).isAutoDeployEnabled();
		verify(resourcePatternResolverMock).getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_BPMN);
		verify(resourcePatternResolverMock).getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_DMN);
		verify(resourcePatternResolverMock).getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_FORM);
		verify(operatonClientMock).deploy(eq(tenant), eq(PROCESSMODEL_FILE), eq(true), eq(true), eq(deploymentName), isNull(), formDataCaptor.capture());

		// The file name has to keep the extension, since the deployer uses it to recognize the resource type
		assertThat(formDataCaptor.getValue().getFileName()).isEqualTo(PROCESSMODEL_FILE);
		assertThat(formDataCaptor.getValue().getData()).isEqualTo(processFile.getContentAsByteArray());
	}

	@Test
	void patternResolverThrowsException() throws IOException {
		final var originException = new IOException("testException");

		when(deploymentPropertiesMock.isAutoDeployEnabled()).thenReturn(true);
		when(deploymentPropertiesMock.getProcesses()).thenReturn(List.of(processArchiveMock));
		when(resourcePatternResolverMock.getResources(any())).thenThrow(originException);

		final var exception = assertThrows(DeploymentException.class, () -> tenantAwareAutoDeployment.deployProcessResources());

		verify(resourcePatternResolverMock).getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_BPMN);
		verifyNoMoreInteractions(resourcePatternResolverMock);
		verifyNoInteractions(operatonClientMock);
		assertThat(exception.getCause()).isEqualTo(originException);
	}

	@Test
	void createDeploymentThrowsException() throws IOException {
		final var name = "name";
		final var originException = new RuntimeException("testException");

		when(deploymentPropertiesMock.isAutoDeployEnabled()).thenReturn(true);
		when(deploymentPropertiesMock.getProcesses()).thenReturn(List.of(processArchiveMock));
		when(processArchiveMock.name()).thenReturn(name);
		doThrow(originException).when(operatonClientMock).deploy(any(), any(), anyBoolean(), anyBoolean(), any(), any(), any());
		when(resourcePatternResolverMock.getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_BPMN)).thenReturn(new Resource[] {
			resourceMock
		});
		when(resourceMock.getFilename()).thenReturn(PROCESSMODEL_FILE);
		when(resourceMock.getInputStream()).thenReturn(new ClassPathResource(PROCESSMODEL_PATH + PROCESSMODEL_FILE).getInputStream());

		final var exception = assertThrows(DeploymentException.class, () -> tenantAwareAutoDeployment.deployProcessResources());

		verify(resourcePatternResolverMock).getResources(DEFAULT_PATTERN_PREFIX + FILETYPE_BPMN);
		verify(operatonClientMock).deploy(any(), any(), anyBoolean(), anyBoolean(), any(), any(), any());
		verifyNoMoreInteractions(resourcePatternResolverMock, operatonClientMock);
		assertThat(exception.getCause()).isEqualTo(originException);
	}
}
