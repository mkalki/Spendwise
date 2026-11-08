package com.spendwise.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record ShoppingSearchRequest(
        @NotBlank @Size(max = 1000)
        String message,
        ShoppingSortBy sortBy,
        @Valid ShoppingSearchFilters filters) {

    public ShoppingSearchRequest {
        if (sortBy == null) {
            sortBy = ShoppingSortBy.LOWEST_PRICE;
        }
        if (filters == null) {
            filters = ShoppingSearchFilters.none();
        }
    }

    public ShoppingSearchRequest(String message) {
        this(message, ShoppingSortBy.LOWEST_PRICE, ShoppingSearchFilters.none());
    }

    public ShoppingSearchRequest(String message, ShoppingSortBy sortBy) {
        this(message, sortBy, ShoppingSearchFilters.none());
    }
}
