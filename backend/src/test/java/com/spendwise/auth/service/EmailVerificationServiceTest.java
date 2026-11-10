package com.spendwise.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.spendwise.auth.entity.EmailVerificationToken;
import com.spendwise.auth.entity.UserAccount;
import com.spendwise.auth.integration.ResendEmailClient;
import com.spendwise.auth.repository.EmailVerificationTokenRepository;
import com.spendwise.auth.repository.UserAccountRepository;
import com.spendwise.common.exception.ApiException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailVerificationServiceTest {

    private final Map<String, EmailVerificationToken> tokenRows = new HashMap<>();
    private final Map<String, UserAccount> accounts = new HashMap<>();
    private final Map<String, String> deliveredLinks = new HashMap<>();
    private final AtomicInteger sends = new AtomicInteger();
    private EmailVerificationTokenRepository tokenRepository;
    private UserAccountRepository accountRepository;
    private ResendEmailClient emailClient;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        tokenRows.clear();
        accounts.clear();
        deliveredLinks.clear();
        sends.set(0);
        tokenRepository = proxy(EmailVerificationTokenRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.ofNullable(tokenRows.get((String) args[0]));
            case "findByTokenHashForUpdate" -> tokenRows.values().stream()
                    .filter(token -> token.getTokenHash().equals(args[0])).findFirst();
            case "saveAndFlush" -> {
                EmailVerificationToken token = (EmailVerificationToken) args[0];
                tokenRows.put(token.getUserId(), token);
                yield token;
            }
            case "delete" -> {
                tokenRows.remove(((EmailVerificationToken) args[0]).getUserId());
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        accountRepository = proxy(UserAccountRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("findById")) return Optional.ofNullable(accounts.get((String) args[0]));
            throw new UnsupportedOperationException(method.getName());
        });
        emailClient = new ResendEmailClient(new SimpleClientHttpRequestFactory(), "", "") {
            @Override
            public void sendVerificationEmail(String recipient, String verificationUrl) {
                sends.incrementAndGet();
                deliveredLinks.put(recipient, verificationUrl);
            }
        };
        service = new EmailVerificationService(tokenRepository, accountRepository, emailClient,
                "https://spendwise.example/");
    }

    @Test
    void issuesRandomLinkAndPersistsOnlyItsSha256Hash() throws Exception {
        UserAccount account = pendingAccount();
        service.issueAndSend(account);

        EmailVerificationToken savedToken = tokenRows.get(account.getId());
        String verificationUrl = deliveredLinks.get(account.getEmail());
        String rawToken = verificationUrl.substring(verificationUrl.indexOf("?token=") + 7);
        assertThat(verificationUrl).startsWith("https://spendwise.example/verify-email?token=");
        assertThat(rawToken).hasSize(43).doesNotContain("=");
        assertThat(savedToken.getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(savedToken.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofHours(23)));
        assertThat(sends.get()).isEqualTo(1);
    }

    @Test
    void suppressesRepeatedResendWithinCooldown() {
        UserAccount account = pendingAccount();
        tokenRows.put(account.getId(), new EmailVerificationToken(
                account.getId(), "a".repeat(64), Instant.now().plusSeconds(3600), Instant.now()));

        service.resendIfAllowed(account);

        assertThat(sends.get()).isZero();
        assertThat(tokenRows.get(account.getId()).getTokenHash()).isEqualTo("a".repeat(64));
    }

    @Test
    void verifiesAnUnexpiredTokenAndConsumesIt() {
        String rawToken = "test-one-time-token";
        UserAccount account = pendingAccount();
        EmailVerificationToken token = new EmailVerificationToken(account.getId(), sha256(rawToken),
                Instant.now().plusSeconds(3600), Instant.now());
        tokenRows.put(account.getId(), token);
        accounts.put(account.getId(), account);

        service.verify(rawToken);

        assertThat(account.isEmailVerified()).isTrue();
        assertThat(tokenRows).doesNotContainKey(account.getId());
    }

    @Test
    void rejectsExpiredTokensWithoutVerifyingTheAccount() {
        String rawToken = "expired-token";
        UserAccount account = pendingAccount();
        EmailVerificationToken token = new EmailVerificationToken(account.getId(), sha256(rawToken),
                Instant.now().minusSeconds(1), Instant.now().minusSeconds(3601));
        tokenRows.put(account.getId(), token);

        assertThatThrownBy(() -> service.verify(rawToken))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("EMAIL_VERIFICATION_INVALID");
        assertThat(account.isEmailVerified()).isFalse();
        assertThat(tokenRows).containsKey(account.getId());
    }

    @Test
    void rejectsUnknownAndAlreadyConsumedTokens() {
        assertThatThrownBy(() -> service.verify("unknown"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("EMAIL_VERIFICATION_INVALID");
    }

    private UserAccount pendingAccount() {
        return UserAccount.passwordAccount("account-1", "buyer@example.com", "buyer", "password-hash");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
