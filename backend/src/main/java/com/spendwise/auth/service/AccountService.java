package com.spendwise.auth.service;

import com.spendwise.auth.dto.AuthResponse;
import com.spendwise.auth.entity.UserAccount;
import com.spendwise.auth.repository.UserAccountRepository;
import com.spendwise.common.exception.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserAccountRepository repository;
    public AccountService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String registerGoogleAccount(OidcUser googleUser) {
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
            return byGoogleSubject.getId();
        }

        UserAccount existingByEmail = repository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existingByEmail != null) {
            if (existingByEmail.getProvider() == UserAccount.Provider.PASSWORD) {
                existingByEmail.linkGoogleIdentity(
                        subject, normalizedEmail, googleUser.getFullName(), googleUser.getPicture());
                return existingByEmail.getId();
            }
            throw accountConflict(
                    "EMAIL_ALREADY_REGISTERED",
                    "This Google email is already connected to another Spendwise account.");
        }

        UserAccount account = repository.saveAndFlush(UserAccount.googleAccount(
                subject,
                normalizedEmail,
                googleUser.getFullName(),
                googleUser.getPicture()));
        return account.getId();
    }

    @Transactional(readOnly = true)
    public AuthResponse profile(String userId) {
        UserAccount account = repository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND", "The signed-in account no longer exists."));
        return toResponse(account);
    }

    @Transactional
    public AuthResponse updateProfile(String userId, String name) {
        UserAccount account = repository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "The signed-in account no longer exists."));
        account.updateDisplayName(name.strip());
        return toResponse(account);
    }

    public AuthResponse toResponse(UserAccount account) {
        return new AuthResponse(
                account.getId(),
                account.getEmail(),
                account.getUsername(),
                account.getProfileName() == null ? account.getDisplayName() : account.getProfileName(),
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
