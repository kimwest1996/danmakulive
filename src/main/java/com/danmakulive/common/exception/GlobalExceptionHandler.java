package com.danmakulive.common.exception;

import com.danmakulive.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ClientException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleClientException(ClientException ex) {
        log.debug("Client exception: code={}, message={}", ex.getErrorCode(), ex.getErrorMessage());
        return Result.failure(ex.getErrorCode(), ex.getErrorMessage());
    }

    @ExceptionHandler(ServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleServiceException(ServiceException ex) {
        log.error("Service exception: code={}, message={}", ex.getErrorCode(), ex.getErrorMessage(), ex);
        return Result.failure(ex.getErrorCode(), ex.getErrorMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknown(Exception ex) {
        log.error("Unknown exception", ex);
        return Result.failure(BaseErrorCode.SYSTEM_ERROR.code(), BaseErrorCode.SYSTEM_ERROR.message());
    }
}
