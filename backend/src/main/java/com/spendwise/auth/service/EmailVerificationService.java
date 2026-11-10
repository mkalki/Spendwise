package com.spendwise.auth.service;

import com.spendwise.auth.entity.EmailVerificationToken;
import com.spendwise.auth.entity.UserAccount;
import com.spendwise.auth.integration.ResendEmailClient;
import com.spendwise.auth.repository.EmailVerificationTokenRepository;
import com.spendwise.auth.repository.UserAccountRepository;
import com.spendwise.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserAccountRepository accountRepository;
    private final ResendEmailClient emailClient;
    private final String frontendOrigin;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserAccountRepository accountRepository,
            ResendEmailClient emailClient,
            @Value("${spendwise.auth.frontend-origin}") String frontendOrigin) {
        this.tokenRepository = tokenRepository;
        this.accountRepository = accountRepository;
        this.emailClient = emailClient;
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
    }

    @Transactional
    public void issueAndSend(UserAccount account) {
        issueAndSend(account, false);
    }

    @Transactional
    public void resendIfAllowed(UserAccount account) {
        issueAndSend(account, true);
    }

    private void issueAndSend(UserAccount account, boolean enforceCooldown) {
        Instant now = Instant.now();
        EmailVerificationToken previous = tokenRepository.findById(account.getId()).orElse(null);
        if (enforceCooldown && previous != null
                && previous.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN))) {
            return;
        }

        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        tokenRepository.saveAndFlush(new EmailVerificationToken(
                account.getId(), hash(rawToken), now.plus(TOKEN_LIFETIME), now));
        emailClient.sendVerificationEmail(account.getEmail(),
                frontendOrigin + "/verify-email?token=" + rawToken);
    }

    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(this::invalidToken);
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            throw invalidToken();
        }
        UserAccount account = accountRepository.findById(token.getUserId())
                .orElseThrow(this::invalidToken);
        account.verifyEmail();
        tokenRepository.delete(token);
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_INVALID",
                "This verification link is invalid or expired. Request a new email to try again.");
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
