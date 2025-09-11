package ru.datana.integration.opc.request;

import java.util.Map;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ValueUpdateRequest {
	private final Map<String, Float> required;	
	private final Map<String, Float> optional;
}
