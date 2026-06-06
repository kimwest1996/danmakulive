package com.danmakulive.common.exception;

public abstract class AbstractException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    protected AbstractException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode.code();
        this.errorMessage = message != null && !message.isBlank() ? message : errorCode.message();
    }

    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
