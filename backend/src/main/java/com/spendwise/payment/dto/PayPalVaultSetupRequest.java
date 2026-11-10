package com.spendwise.payment.dto;

import jakarta.validation.constraints.AssertTrue;

public record PayPalVaultSetupRequest(@AssertTrue boolean consent) { }
