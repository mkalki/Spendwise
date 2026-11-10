package com.spendwise.integration.gemini.dto;

import java.util.UUID;

public record GeminiRecommendationCandidate(
        UUID candidateId,
        String productTitle,
        String brand,
        String merchantName,
        String price,
        String currencyCode) {
    public GeminiRecommendationCandidate(UUID candidateId, String productTitle, String brand) {
        this(candidateId, productTitle, brand, null, null, null);
    }
}
