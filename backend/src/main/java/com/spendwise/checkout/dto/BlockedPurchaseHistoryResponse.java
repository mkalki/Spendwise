package com.spendwise.checkout.dto;

import java.time.Instant;
import java.util.List;

public record BlockedPurchaseHistoryResponse(List<Entry> checkoutAttempts, List<Entry> rejectedOffers) {
    public record Entry(String id, String productName, String amount, String currency, String reason, Instant createdAt) {
    }
}
