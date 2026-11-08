package com.spendwise.payment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentRequest(@NotNull UUID searchSessionId, @NotNull UUID candidateId) {
}
