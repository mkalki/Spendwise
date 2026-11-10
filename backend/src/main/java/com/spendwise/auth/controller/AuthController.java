package com.spendwise.auth.controller;

import com.spendwise.auth.dto.AuthResponse;
import com.spendwise.auth.dto.LoginRequest;
import com.spendwise.auth.dto.SignupRequest;
import com.spendwise.auth.entity.UserAccount;
import com.spendwise.auth.service.AccountService;
import com.spendwise.auth.service.SessionAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Password and Google signup, login, session, and CSRF APIs.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;
    private final SessionAuthenticationService sessionAuthenticationService;

    public AuthController(
            AccountService accountService,
            SessionAuthenticationService sessionAuthenticationService) {
        this.accountService = accountService;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token", description = "Call before signup, login, logout, or another state-changing request.")
    public CsrfResponse csrfToken(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/signup")
    @Operation(summary = "Create a password account", description = "Creates the account and starts an authenticated session.")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        UserAccount account = accountService.signUp(request);
        Authentication authentication = sessionAuthenticationService.authenticate(
                account.getEmail(), request.password(), servletRequest, servletResponse);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.profile(authentication.getName()));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email or username", description = "Authenticates and starts an HTTP session.")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = sessionAuthenticationService.authenticate(
                request.identifier(), request.password(), servletRequest, servletResponse);
        return accountService.profile(authentication.getName());
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
