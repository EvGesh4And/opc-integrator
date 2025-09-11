package ru.datana.integration.opc.exception;

public class DataProcessingException extends RuntimeException {
	private static final long serialVersionUID = -5437769736008008031L;
	
	public DataProcessingException(String message) {
		super(message);
	}
}
