package com.spendwise.payment.dto;

import java.time.Instant;
import java.util.UUID;

public record SavedPayPalMethodResponse(UUID id, String type, String paypalEmail, String status, Instant createdAt) { }
