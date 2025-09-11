package ru.datana.integration.opc.request;

import java.util.Set;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder
public class SubscribeRequest {
	private final Set<String> keys;
}
