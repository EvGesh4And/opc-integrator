package ru.datana.integration.opc.exception;

public class InternalErrorException extends RuntimeException {
	private static final long serialVersionUID = -3485800341971640409L;

	public InternalErrorException(String message) {
		super(message);
	}
}	
