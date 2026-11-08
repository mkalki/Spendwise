package com.spendwise.policy.dto;

import com.spendwise.policy.entity.SpendingPolicy;

public record SpendingPolicyResponse(
        String maxProductPrice,
        String maxOrderAmount,
        String weeklyBudget,
        int maxOrdersPerWeek,
        String countryCode,
        String currencyCode,
        String timezone,
        boolean active) {

    public static SpendingPolicyResponse from(SpendingPolicy policy) {
        return new SpendingPolicyResponse(
                policy.getMaxProductPrice().toPlainString(),
                policy.getMaxOrderAmount().toPlainString(),
                policy.getWeeklyBudget().toPlainString(),
                policy.getMaxOrdersPerWeek(),
                policy.getCountryCode(),
                policy.getCurrencyCode(),
                policy.getTimezone(),
                policy.isActive());
    }
}
