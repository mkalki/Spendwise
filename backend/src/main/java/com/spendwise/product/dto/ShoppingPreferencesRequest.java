package com.spendwise.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ShoppingPreferencesRequest(
        @Size(max = 20) List<@Size(max = 80) String> preferredCategories,
        @Size(max = 20) List<@Size(max = 80) String> preferredBrands,
        @Size(max = 20) List<@Size(max = 120) String> preferredRetailers,
        @Size(max = 20) List<@Size(max = 100) String> keywords,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal maxPrice,
        @Pattern(regexp = "[A-Z]{3}") String currencyCode) {
}
