package ru.datana.integration.opc.exception;

import static java.util.stream.Collectors.toSet;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import lombok.NoArgsConstructor;
import ru.datana.integration.opc.controller.ModelController;
import ru.datana.integration.opc.controller.TestController;
import ru.datana.integration.opc.exception.APIError.AdditionalInfo;
import ru.datana.integration.opc.exception.SubscriptionException.ErrorDescription;

/**
 * if exception processed at ResponseEntityExceptionHandler.handleException(...)
 * then one and the only is to extend ResponseEntityExceptionHandler
 * 
 * @see ResponseEntityExceptionHandler.handleException
 */

@RestControllerAdvice(basePackageClasses = { ModelController.class, TestController.class })
@NoArgsConstructor(access = PRIVATE)
public class GlobalExceptionHandler {
	private static final String MAPPING = "MAPPING";
	private static final int CODE_INTERNAL_ERROR = 101;
	private static final int CODE_PARAM_VALIDATION = 200;

	private static final int CODE_CLIENT_ERROR = 102;

	private static final String INVALID_VALUE_ERROR = "Invalid value for field '%s'";
	private static final String INVALID_REQUEST_BODY = "Invalid request body";
	private static final String INVALID_PATH_VARIABLE = "Invalid value provided for path variable(s)";

	/**
	 * Override exception handler for ValidationException
	 */
	@ExceptionHandler
	public ResponseEntity<APIError> handle(MethodArgumentNotValidException e) {
		return buildResponse(BAD_REQUEST, CODE_PARAM_VALIDATION,
				e.getBindingResult().getAllErrors().getFirst().getDefaultMessage());
	}

	/**
	 * Override exception handler for HttpMessageNotReadableException
	 */
	@ExceptionHandler
	protected ResponseEntity<APIError> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
		if (e.getCause() instanceof InvalidFormatException cause) {
			String fieldName = cause.getPath().get(0).getFieldName();
			String message = INVALID_VALUE_ERROR.formatted(fieldName);
			return buildResponse(BAD_REQUEST, CODE_PARAM_VALIDATION, message);
		}
		return buildResponse(BAD_REQUEST, CODE_CLIENT_ERROR, INVALID_REQUEST_BODY);
	}

	/**
	 * Override exception handler for HttpServerErrorException
	 */
	@ExceptionHandler
	protected ResponseEntity<APIError> handleHttpServerErrorException(HttpServerErrorException e) {
		return buildResponse(HttpStatus.valueOf(e.getStatusCode().value()), CODE_INTERNAL_ERROR, e.getStatusText());
	}

	/**
	 * Override exception handler for IllegalArgumentException
	 */
	@ExceptionHandler
	public ResponseEntity<APIError> handle(IllegalArgumentException e) {
		return buildResponse(BAD_REQUEST, CODE_PARAM_VALIDATION, INVALID_PATH_VARIABLE);
	}

	/**
	 * Override exception handler for MissingServletRequestParameterException
	 */
	@ExceptionHandler
	public ResponseEntity<APIError> handle(MissingServletRequestParameterException e) {
		return buildResponse(BAD_REQUEST, CODE_PARAM_VALIDATION, e.getMessage());
	}

	/**
	 * Override exception handler for ResourceNotFoundException
	 */
	@ExceptionHandler
	public ResponseEntity<APIError> handle(ResourceNotFoundException e) {
		return buildResponse(NOT_FOUND, e.getCode(), e.getMessage());
	}

	/**
	 * Override exception handler for InternalErrorException
	 */
	@ExceptionHandler
	protected ResponseEntity<APIError> handleInternalErrorException(InternalErrorException e) {
		return buildResponse(INTERNAL_SERVER_ERROR, CODE_INTERNAL_ERROR, e.getMessage());
	}

	/**
	 * Override exception handler for the custom ValidException
	 */
	@ExceptionHandler
	public ResponseEntity<APIError> handle(ValidException e) {
		return buildResponse(BAD_REQUEST, e.getCode(), e.getMessage(), e.getFieldErrors());
	}

	/**
	 * Override exception handler for the custom SubscriptionException
	 */
	@ExceptionHandler
	public ResponseEntity<APIError> handle(SubscriptionException e) {
		return buildResponse(BAD_REQUEST, e.getErrors());
	}

	private static ResponseEntity<APIError> buildResponse(HttpStatus httpStatus, int customCode, String description) {
		return new ResponseEntity<>(new APIError(httpStatus.value(), customCode, description), httpStatus);
	}

	private ResponseEntity<APIError> buildResponse(HttpStatus httpStatus, Set<ErrorDescription> errors) {
		var infos = errors.stream().map(
				ed -> AdditionalInfo.builder().field(ed.getId()).message(ed.getMessage()).subCode(ed.getType()).build())
				.collect(toSet());
		return new ResponseEntity<>(new APIError(httpStatus.value(), 400, MAPPING, infos), httpStatus);
	}

	private ResponseEntity<APIError> buildResponse(HttpStatus httpStatus, int code, String message,
			Map<String, String> fieldErrors) {
		var infos = fieldErrors.entrySet().stream()
				.map(e -> AdditionalInfo.builder().field(e.getKey()).subCode("404").message(e.getValue()).build())
				.collect(toSet());
		return new ResponseEntity<>(new APIError(httpStatus.value(), code, message, infos), httpStatus);
	}
}
