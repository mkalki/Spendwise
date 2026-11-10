package com.spendwise.auth.service;

import com.spendwise.auth.dto.AuthResponse;
import com.spendwise.auth.dto.SignupResponse;
import com.spendwise.auth.dto.SignupRequest;
import com.spendwise.auth.entity.UserAccount;
import com.spendwise.auth.repository.UserAccountRepository;
import com.spendwise.common.exception.ApiException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public AccountService(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public SignupResponse signUp(SignupRequest request) {
        String email = normalize(request.email());
        String username = normalize(request.username());

        if (repository.findByEmailIgnoreCase(email).isPresent()) {
            throw accountConflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
        }
        if (repository.findByUsernameIgnoreCase(username).isPresent()) {
            throw accountConflict("USERNAME_ALREADY_TAKEN", "This username is already taken.");
        }

        UserAccount account = repository.saveAndFlush(UserAccount.passwordAccount(
                UUID.randomUUID().toString(),
                email,
                username,
                passwordEncoder.encode(request.password())));
        emailVerificationService.issueAndSend(account);
        return new SignupResponse(email, "Check your inbox for a verification link before signing in.");
    }

    @Transactional
    public void verifyEmail(String token) {
        emailVerificationService.verify(token);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        repository.findByEmailIgnoreCase(normalize(email))
                .filter(account -> account.getProvider() == UserAccount.Provider.PASSWORD)
                .filter(account -> !account.isEmailVerified())
                .ifPresent(emailVerificationService::resendIfAllowed);
    }

    @Transactional
    public void registerGoogleAccount(OidcUser googleUser) {
        String subject = googleUser.getSubject();
        String email = googleUser.getEmail();
        Boolean emailVerified = googleUser.getClaimAsBoolean("email_verified");
        if (email == null || !Boolean.TRUE.equals(emailVerified)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "GOOGLE_EMAIL_NOT_VERIFIED",
                    "Google sign-in requires a verified email address.");
        }

        String normalizedEmail = normalize(email);
        UserAccount byGoogleSubject = repository.findByGoogleSubject(subject).orElse(null);
        if (byGoogleSubject != null) {
            byGoogleSubject.updateGoogleProfile(
                    normalizedEmail,
                    googleUser.getFullName(),
                    googleUser.getPicture());
            return;
        }

        if (repository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw accountConflict(
                    "EMAIL_ALREADY_REGISTERED",
                    "This email belongs to an existing account. Sign in with that account first.");
        }

        repository.saveAndFlush(UserAccount.googleAccount(
                subject,
                normalizedEmail,
                googleUser.getFullName(),
                googleUser.getPicture()));
    }

    @Transactional(readOnly = true)
    public AuthResponse profile(String userId) {
        UserAccount account = repository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND", "The signed-in account no longer exists."));
        return toResponse(account);
    }

    public AuthResponse toResponse(UserAccount account) {
        return new AuthResponse(
                account.getId(),
                account.getEmail(),
                account.getUsername(),
                account.getDisplayName(),
                account.getPictureUrl(),
                account.getProvider().name());
    }

    private ApiException accountConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
