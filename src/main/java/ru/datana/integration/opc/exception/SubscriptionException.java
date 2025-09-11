package ru.datana.integration.opc.exception;

import java.util.Set;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class SubscriptionException extends RuntimeException {
	private static final long serialVersionUID = -2700792221450003750L;

	@Value
	public static class ErrorDescription {
		private String message;
		private String type;
		private String id;
	}

	private Set<ErrorDescription> errors;

	public SubscriptionException(Set<ErrorDescription> errors) {
		this.errors = errors;
	}

	@Override
	public String getMessage() {
		return errors.stream().map(e -> "[%s:%s] %s".formatted(e.getType(), e.getId(), e.getMessage()))
				.collect(Collectors.joining(", "));
	}
}
