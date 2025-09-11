package ru.datana.integration.opc.exception;

import java.util.Map;

import jakarta.validation.ValidationException;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class ValidException extends ValidationException {
	private static final long serialVersionUID = 2338795726484889154L;

	private final int code;
	private final Map<String,String> fieldErrors;
	
	@Builder
	public ValidException(int code, String message, Map<String, String> fieldErrors) {
		super(message);
		this.code = code;
		this.fieldErrors = fieldErrors;
	}
}
