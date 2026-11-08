package com.spendwise.checkout.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCheckoutRequest(
        @NotNull UUID searchSessionId,
        @NotNull UUID candidateId) {
}
