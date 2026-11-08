package com.spendwise.policy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SpendingPolicyRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal maxProductPrice,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal maxOrderAmount,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal weeklyBudget,
        @Min(1) @Max(1000)
        int maxOrdersPerWeek,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$")
        String currencyCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$")
        String countryCode,
        @NotBlank @Size(max = 64)
        String timezone) {
}
