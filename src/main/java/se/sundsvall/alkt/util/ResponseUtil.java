package se.sundsvall.alkt.util;

import java.net.URI;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import se.sundsvall.dept44.problem.Problem;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

public final class ResponseUtil {

	private ResponseUtil() {}

	public static String getIdOfCreatedResource(final ResponseEntity<Void> response, final String service) {
		return Optional.ofNullable(response.getHeaders().getLocation())
			.map(URI::getPath)
			.map(path -> substringAfterLast(path, "/"))
			.filter(StringUtils::isNotBlank)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "%s created a resource without saying which".formatted(service)));
	}
}
