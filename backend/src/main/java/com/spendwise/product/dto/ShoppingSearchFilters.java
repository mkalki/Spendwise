package com.spendwise.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ShoppingSearchFilters(
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minPrice,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal maxPrice,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> categoryIds,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> websiteIds,
        Availability availability,
        Boolean onlyOnSale) {

    public static ShoppingSearchFilters none() {
        return new ShoppingSearchFilters(null, null, List.of(), List.of(), null, false);
    }

    public enum Availability {
        IN_STOCK,
        OUT_OF_STOCK
    }
}
