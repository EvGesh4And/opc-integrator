package ru.datana.integration.opc.request;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonInclude(NON_NULL)
public class MappingDesc {
	@Schema(description = "Variable name (at business level)", requiredMode =  REQUIRED, example = "temperature-at-top")
	@NotBlank
	String key;
	@Schema(description = "OPC namespace identifier", requiredMode =  REQUIRED, example = "2")
	@NotNull
	@Positive
	@Builder.Default
	int namespaceIndex = 0;
	@Schema(description = "OPC tag (STRING) identifier, tag part", requiredMode =  NOT_REQUIRED, example = "model1.power.mv")
	String tag;
	@Schema(description = "OPC tag (STRING) identifier, attribute part", requiredMode =  NOT_REQUIRED, example = "predict")
	String attribute;
	@Schema(description = "OPC tag (INTEGER) identifier", requiredMode =  NOT_REQUIRED, example = "15")
	Short nodeId;
	@Schema(description = "OPC tag (UUID) identifier", requiredMode =  NOT_REQUIRED, example = "01234567-0123-0123-0123-0123456789ab")
	UUID uuid;
	@Schema(description = "OPC tag (OPAQUE) identifier", requiredMode =  NOT_REQUIRED, example = "level-5-0001")
	String bytes;
}
