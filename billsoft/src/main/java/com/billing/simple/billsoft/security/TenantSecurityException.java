package com.billing.simple.billsoft.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TenantSecurityException extends RuntimeException {
    public TenantSecurityException(String message) {
        super(message);
    }
}
