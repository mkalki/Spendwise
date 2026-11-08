package com.spendwise.policy.engine;

import com.spendwise.common.exception.ApiException;
import com.spendwise.policy.entity.SpendingPolicy;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PolicyEngine {

    public BigDecimal effectiveMaxProductPrice(SpendingPolicy policy, BigDecimal requestedMaxPrice) {
        return requestedMaxPrice == null
                ? policy.getMaxProductPrice()
                : policy.getMaxProductPrice().min(requestedMaxPrice);
    }

    public String productRejectionReason(
            BigDecimal price,
            String currencyCode,
            String availability,
            String merchantName,
            String offerUrl,
            SpendingPolicy policy,
            BigDecimal effectiveMaxPrice) {
        if (price == null || price.signum() <= 0 || currencyCode == null) {
            return "Offer has no valid price or currency.";
        }
        if (!policy.getCurrencyCode().equals(currencyCode)) {
            return "Offer currency does not match the active policy currency.";
        }
        if (price.compareTo(effectiveMaxPrice) > 0) {
            return "Offer exceeds the effective maximum product price.";
        }
        if (!"InStock".equals(availability)) {
            return "Offer is not confirmed in stock.";
        }
        if (merchantName == null || merchantName.isBlank()) {
            return "Offer has no valid merchant.";
        }
        if (!isHttpUrl(offerUrl)) {
            return "Offer does not have a valid retailer URL.";
        }
        return null;
    }

    public void authorizeCheckout(
            SpendingPolicy policy,
            BigDecimal amount,
            BigDecimal effectiveMaxProductPrice,
            String currencyCode,
            BigDecimal completedSpend,
            BigDecimal activeReservations,
            long completedOrders,
            long activeReservationCount) {
        if (!policy.isActive()) {
            throw conflict("POLICY_INACTIVE", "The active spending policy is disabled.");
        }
        if (!policy.getCurrencyCode().equals(currencyCode)) {
            throw conflict("CURRENCY_MISMATCH", "Offer currency does not match the active policy.");
        }
        if (amount.compareTo(policy.getMaxProductPrice()) > 0) {
            throw policyLimit(
                    "The product exceeds the maximum product price.",
                    policy.getMaxProductPrice(),
                    currencyCode);
        }
        if (amount.compareTo(effectiveMaxProductPrice) > 0) {
            throw policyLimit(
                    "The refreshed offer exceeds the maximum from the original shopping request.",
                    effectiveMaxProductPrice,
                    currencyCode);
        }
        if (amount.compareTo(policy.getMaxOrderAmount()) > 0) {
            throw policyLimit(
                    "The order exceeds the maximum order amount.",
                    policy.getMaxOrderAmount(),
                    currencyCode);
        }
        if (completedSpend.add(activeReservations).add(amount).compareTo(policy.getWeeklyBudget()) > 0) {
            throw policyLimit(
                    "The checkout exceeds the remaining weekly budget.",
                    policy.getWeeklyBudget(),
                    currencyCode);
        }
        if (completedOrders + activeReservationCount >= policy.getMaxOrdersPerWeek()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ORDER_FREQUENCY_LIMIT_EXCEEDED",
                    "The weekly order limit has been reached.",
                    java.util.Map.of("limit", policy.getMaxOrdersPerWeek()));
        }
    }

    private boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            String scheme = URI.create(value).getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ApiException policyLimit(String message, BigDecimal limit, String currency) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "POLICY_LIMIT_EXCEEDED",
                message,
                java.util.Map.of("limit", limit.toPlainString(), "currency", currency));
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
