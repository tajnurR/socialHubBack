package com.socialhub.socialhubBackend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Consistent error payload returned by {@code GlobalExceptionHandler} for every
 * failed request.
 *
 * @param timestamp  when the error occurred
 * @param status     HTTP status code
 * @param error      HTTP status reason phrase
 * @param message    human-readable description
 * @param path       request URI that produced the error
 * @param fieldErrors per-field validation errors (omitted when empty)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponse of(
            int status, String error, String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }

    /** A single field-level validation failure. */
    public record FieldError(String field, String message) {}
}
