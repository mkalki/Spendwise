package com.spendwise.payment.dto;

import java.time.Instant;

public record ProMembershipResponse(boolean pro, String plan, Instant expiresAt, String nextMonthlyPrice) { }
