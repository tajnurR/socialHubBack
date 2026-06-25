package com.socialhub.socialhubBackend.common.exception;

/** Thrown when a requested entity cannot be found. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found: %s".formatted(resource, id));
    }
}
