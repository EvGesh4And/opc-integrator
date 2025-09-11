package ru.datana.integration.opc.exception;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

@AllArgsConstructor(onConstructor = @__({ @JsonCreator }))
@Getter
@ToString
@JsonInclude(NON_EMPTY)
public class APIError {
	public APIError(int httpStatus, int customCode, String description, Set<AdditionalInfo> details) {
		code = "%03d.%03d".formatted(httpStatus, customCode);
		this.description = description;
		this.details = details;
	}

	public APIError(int httpStatus, int customCode, String description) {
		this(httpStatus, customCode, description, Set.of());
	}

	@Value
	@Builder
	@JsonInclude(NON_NULL)
	public static class AdditionalInfo {
		@Schema(description = "Error sub code", requiredMode = Schema.RequiredMode.REQUIRED, example = "400.1")
		private final String subCode;
		@Schema(description = "Field name", requiredMode = Schema.RequiredMode.REQUIRED, example = "key")
		private final String field;
		@Schema(description = "Error message", requiredMode = Schema.RequiredMode.REQUIRED, example = "Filed value can't be empty or blank")
		private final String message;
	}

	@Schema(description = "Error code", requiredMode = Schema.RequiredMode.REQUIRED, example = "400")
	private final String code;
	@Schema(description = "Error description", requiredMode = Schema.RequiredMode.REQUIRED, example = "Validation Error")
	private final String description;
	@Schema(description = "Details", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private final Set<AdditionalInfo> details;
}
