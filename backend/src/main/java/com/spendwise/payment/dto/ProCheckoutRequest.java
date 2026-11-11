package com.spendwise.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProCheckoutRequest(@NotBlank @Pattern(regexp = "MONTHLY|ANNUAL") String plan) { }
