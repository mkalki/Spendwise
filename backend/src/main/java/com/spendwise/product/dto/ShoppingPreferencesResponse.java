package com.spendwise.product.dto;

import com.spendwise.product.entity.ShoppingPreferences;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ShoppingPreferencesResponse(
        List<String> preferredCategories,
        List<String> preferredBrands,
        List<String> preferredRetailers,
        List<String> keywords,
        BigDecimal maxPrice,
        String currencyCode,
        Instant updatedAt) {

    public static ShoppingPreferencesResponse empty() {
        return new ShoppingPreferencesResponse(List.of(), List.of(), List.of(), List.of(), null, null, null);
    }

    public static ShoppingPreferencesResponse from(ShoppingPreferences entity, List<String> categories,
            List<String> brands, List<String> retailers, List<String> keywords) {
        return new ShoppingPreferencesResponse(categories, brands, retailers, keywords,
                entity.getMaxPrice(), entity.getCurrencyCode(), entity.getUpdatedAt());
    }
}
