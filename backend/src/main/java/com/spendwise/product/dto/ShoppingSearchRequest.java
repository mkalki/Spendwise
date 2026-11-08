package com.spendwise.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShoppingSearchRequest(
        @NotBlank @Size(max = 1000)
        String message) {
}
