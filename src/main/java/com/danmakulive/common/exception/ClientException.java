package com.danmakulive.common.exception;

public class ClientException extends AbstractException {

    public ClientException(String message) {
        this(message, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(ErrorCode errorCode) {
        this(errorCode.message(), errorCode);
    }

    public ClientException(String message, ErrorCode errorCode) {
        super(message, null, errorCode);
    }
}
