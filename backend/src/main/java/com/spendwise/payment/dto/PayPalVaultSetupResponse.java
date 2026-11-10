package com.spendwise.payment.dto;

import java.util.UUID;

public record PayPalVaultSetupResponse(UUID setupSessionId, String approvalUrl, String status) { }
