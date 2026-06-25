package com.socialhub.socialhubBackend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Generic domain/business-rule violation. Carries the HTTP status the global
 * handler should translate it into (defaults to 400 Bad Request).
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
