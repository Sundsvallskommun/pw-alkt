package se.sundsvall.alkt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.service.ProcessService;
import se.sundsvall.dept44.common.validators.annotation.ValidMunicipalityId;
import se.sundsvall.dept44.common.validators.annotation.ValidNamespace;
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
@Tag(name = "Process endpoints", description = "Endpoints for driving the processes of the alcohol and tobacco domain")
class ProcessResource {

	private final ProcessService service;

	ProcessResource(ProcessService service) {
		this.service = service;
	}

	@PostMapping(path = "errand-events", consumes = APPLICATION_JSON_VALUE)
	@Operation(description = """
		Take an errand event from Support Management and act on it: start a process for the errand, move a running one \
		along, or delete it when the errand is gone. An event that needs no action is accepted as well.""")
	@ApiResponse(responseCode = "202", description = "Accepted - handled, or deliberately ignored", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
		Problem.class, ConstraintViolationProblem.class
	})))
	@ApiResponse(responseCode = "422",
		description = "The process key matches no deployed process definition. Permanent - do not deliver again",
		content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	@ApiResponse(responseCode = "500", description = "Internal Server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	@ApiResponse(responseCode = "502", description = "Bad Gateway", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	ResponseEntity<Void> handleErrandEvent(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "namespace", description = "Namespace", example = "my.namespace") @ValidNamespace @PathVariable final String namespace,
		@Valid @RequestBody final ErrandEvent errandEvent) {

		service.handleErrandEvent(municipalityId, namespace, errandEvent);

		return accepted()
			.header(CONTENT_TYPE, ALL_VALUE)
			.build();
	}
}
