package ru.datana.integration.opc.request;

import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ModelMeta {
	@NotBlank
	String name;
	@NotEmpty
	@Valid
	Set<MappingDesc> mappings;
}
