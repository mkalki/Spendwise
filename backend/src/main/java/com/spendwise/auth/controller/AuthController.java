package com.spendwise.auth.controller;

import com.spendwise.auth.dto.AuthResponse;
import com.spendwise.auth.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Google registration, sign-in, session, and CSRF APIs.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;
    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token", description = "Call before logout or another state-changing request.")
    public CsrfResponse csrfToken(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/google/login")
    @Operation(summary = "Start Google login", description = "Redirects to Google. Existing Google accounts sign in; first-time users are registered automatically.")
    public ResponseEntity<Void> googleLogin() {
        return googleAuthorizationRedirect();
    }

    @GetMapping("/google/signup")
    @Operation(summary = "Start Google signup", description = "Redirects to Google. A verified Google identity is registered on first sign-in.")
    public ResponseEntity<Void> googleSignup() {
        return googleAuthorizationRedirect();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the signed-in account")
    public AuthResponse currentUser(@AuthenticationPrincipal(expression = "name") String userId) {
        return accountService.profile(userId);
    }

    private ResponseEntity<Void> googleAuthorizationRedirect() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/google"))
                .build();
    }

    public record CsrfResponse(String headerName, String token) {
    }
}
