package com.spendwise.auth.service;

import org.springframework.security.core.AuthenticationException;

public class EmailNotVerifiedAuthenticationException extends AuthenticationException {
    public EmailNotVerifiedAuthenticationException() {
        super("Email verification is required.");
    }
}
