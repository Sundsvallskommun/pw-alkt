package se.sundsvall.alkt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.alkt.api.model.StartProcessResponse;
import se.sundsvall.alkt.service.ProcessService;
import se.sundsvall.dept44.common.validators.annotation.ValidMunicipalityId;
import se.sundsvall.dept44.common.validators.annotation.ValidNamespace;
import se.sundsvall.dept44.common.validators.annotation.ValidUuid;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.ResponseEntity.accepted;

@RestController
@Validated
@RequestMapping("{municipalityId}/{namespace}/process")
@Tag(name = "Process endpoints", description = "Endpoints for starting and updating processes")
class ProcessResource {

	private final ProcessService service;

	ProcessResource(ProcessService service) {
		this.service = service;
	}

	@PostMapping(path = "start/{errandId}", produces = APPLICATION_JSON_VALUE)
	@Operation(description = "Start a new process instance for the provided errandId")
	@ApiResponse(responseCode = "202", description = "Accepted", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
		Problem.class, ConstraintViolationProblem.class
	})))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	@ApiResponse(responseCode = "500", description = "Internal Server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	@ApiResponse(responseCode = "502", description = "Bad Gateway", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	ResponseEntity<StartProcessResponse> startProcess(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "namespace", description = "Namespace", example = "my.namespace") @ValidNamespace @PathVariable final String namespace,
		@Parameter(name = "errandId", description = "Support Management errand ID", example = "f0882f1d-06bc-47fd-b017-1d8307f5ce95") @ValidUuid @PathVariable final String errandId) {

		final var startProcessResponse = new StartProcessResponse(service.startProcess(municipalityId, namespace, errandId));

		return accepted().body(startProcessResponse);
	}

	@PostMapping(path = "update/{processInstanceId}")
	@Operation(description = "Update a process instance matching the provided processInstanceId")
	@ApiResponse(responseCode = "202", description = "Accepted", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
		Problem.class, ConstraintViolationProblem.class
	})))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	@ApiResponse(responseCode = "500", description = "Internal Server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	@ApiResponse(responseCode = "502", description = "Bad Gateway", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	ResponseEntity<Void> updateProcess(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "namespace", description = "Namespace", example = "my.namespace") @ValidNamespace @PathVariable final String namespace,
		@Parameter(name = "processInstanceId") @PathVariable @ValidUuid final String processInstanceId) {

		service.updateProcess(municipalityId, namespace, processInstanceId);

		return accepted()
			.header(CONTENT_TYPE, ALL_VALUE)
			.build();
	}
}
