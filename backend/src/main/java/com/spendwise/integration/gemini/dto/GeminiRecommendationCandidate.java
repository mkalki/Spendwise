package com.spendwise.integration.gemini.dto;

import com.spendwise.product.dto.ShoppingSearchResponse.ProductRating;
import java.util.UUID;

public record GeminiRecommendationCandidate(
        UUID candidateId,
        String productTitle,
        String brand,
        String merchantName,
        String price,
        String currencyCode,
        ProductRating rating) {
    public GeminiRecommendationCandidate(UUID candidateId, String productTitle, String brand,
            String merchantName, String price, String currencyCode) {
        this(candidateId, productTitle, brand, merchantName, price, currencyCode, null);
    }

    public GeminiRecommendationCandidate(UUID candidateId, String productTitle, String brand) {
        this(candidateId, productTitle, brand, null, null, null);
    }
}
