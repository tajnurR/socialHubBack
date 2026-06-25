package com.socialhub.socialhubBackend.integration.core.exception;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a platform rejects the stored credentials (expired/invalidated
 * token). Signals that the integration needs re-authentication; the service
 * marks it {@code REAUTH_REQUIRED}. Maps to HTTP 401.
 */
public class ProviderAuthException extends BusinessException {

    public ProviderAuthException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
