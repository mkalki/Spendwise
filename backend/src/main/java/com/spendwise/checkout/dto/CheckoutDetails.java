package com.spendwise.checkout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutDetails(
        @NotNull @Valid Address shipping,
        @Valid Address billing) {

    public record Address(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 160) String line1,
            @Size(max = 160) String line2,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 24) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "Enter a two-letter country code.")
                    String countryCode) {
    }
}
