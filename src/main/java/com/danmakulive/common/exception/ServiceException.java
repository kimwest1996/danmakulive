package com.danmakulive.common.exception;

public class ServiceException extends AbstractException {

    public ServiceException(String message) {
        this(message, BaseErrorCode.SYSTEM_ERROR);
    }

    public ServiceException(String message, ErrorCode errorCode) {
        super(message, null, errorCode);
    }
}
