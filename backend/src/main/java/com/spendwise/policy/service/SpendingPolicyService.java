package com.spendwise.policy.service;

import com.spendwise.common.exception.ApiException;
import com.spendwise.policy.dto.SpendingPolicyRequest;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.repository.SpendingPolicyRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpendingPolicyService {

    private static final Set<String> CHANNEL3_CURRENCIES =
            Set.of("USD", "CAD", "AUD", "GBP", "EUR", "SEK", "CZK", "RON", "CHF", "PLN");
    private static final Set<String> CHANNEL3_COUNTRIES = Set.of(
            "US", "GB", "EU", "AU", "CA", "IE", "DE", "AT", "FR", "BE", "IT",
            "ES", "NL", "SE", "FI", "PT", "CZ", "GR", "RO", "CH", "PL");

    private final SpendingPolicyRepository repository;

    public SpendingPolicyService(SpendingPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SpendingPolicy getActive(String ownerId) {
        return repository.findByOwnerId(ownerId)
                .orElseGet(() -> repository.save(new SpendingPolicy(
                        ownerId,
                        new BigDecimal("60.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("150.00"),
                        1,
                        "USD",
                        "US",
                        "UTC")));
    }

    @Transactional
    public SpendingPolicy save(String ownerId, SpendingPolicyRequest request) {
        validateRequest(request);
        SpendingPolicy policy = repository.findByOwnerId(ownerId).orElse(null);
        if (policy == null) {
            policy = new SpendingPolicy(
                    ownerId,
                    request.maxProductPrice(),
                    request.maxOrderAmount(),
                    request.weeklyBudget(),
                    request.maxOrdersPerWeek(),
                    request.currencyCode(),
                    request.countryCode(),
                    request.timezone());
        } else {
            policy.update(
                    request.maxProductPrice(),
                    request.maxOrderAmount(),
                    request.weeklyBudget(),
                    request.maxOrdersPerWeek(),
                    request.currencyCode(),
                    request.countryCode(),
                    request.timezone());
        }
        return repository.save(policy);
    }

    private void validateRequest(SpendingPolicyRequest request) {
        if (request.maxProductPrice().compareTo(request.maxOrderAmount()) > 0
                || request.maxOrderAmount().compareTo(request.weeklyBudget()) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_POLICY_LIMITS",
                    "Limits must satisfy maxProductPrice <= maxOrderAmount <= weeklyBudget.");
        }
        try {
            Currency.getInstance(request.currencyCode());
            ZoneId.of(request.timezone());
            if (!CHANNEL3_CURRENCIES.contains(request.currencyCode())
                    || !CHANNEL3_COUNTRIES.contains(request.countryCode())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_POLICY_LOCALE", "Currency or timezone is not supported.");
        }
    }
}
