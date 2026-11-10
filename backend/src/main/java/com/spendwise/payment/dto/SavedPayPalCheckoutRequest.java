package com.spendwise.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SavedPayPalCheckoutRequest(
        @NotNull UUID searchSessionId,
        @NotNull UUID candidateId,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String clientMetadataId) { }
