package ru.datana.integration.opc.exception;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ResourceNotFoundException extends RuntimeException {
	private static final long serialVersionUID = -5274348159746137214L;
	private static final int CODE = 404;

	private final String type;
	private final String id;

	public int getCode() {
		return CODE;
	}
	
	@Override
	public String getMessage() {
		return "%s is not found for %s".formatted(type, id);
	}
}
