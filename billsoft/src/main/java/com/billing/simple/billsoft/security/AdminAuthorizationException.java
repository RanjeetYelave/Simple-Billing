package com.billing.simple.billsoft.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an administrative operation (such as soft unlinking or canonical identity modification)
 * fails administrator authentication/PIN verification. Returns HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AdminAuthorizationException extends RuntimeException {
    public AdminAuthorizationException(String message) {
        super(message);
    }
}

