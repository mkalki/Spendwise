package com.spendwise.product.dto;

import java.util.List;
import java.util.UUID;

public record ShoppingSearchResponse(
        UUID searchSessionId,
        ShoppingIntent intent,
        List<ProductCandidateView> eligibleCandidates,
        List<ProductCandidateView> rejectedCandidates,
        String recommendation,
        String comparisonSummary,
        ShoppingSortBy sortBy) {

    public record ShoppingIntent(
            String normalizedQuery,
            String keywords,
            String requestedMaxPrice,
            String currencyCode,
            String countryCode) {
    }

    public record ProductCandidateView(
            UUID candidateId,
            String channel3ProductId,
            String productTitle,
            String brand,
            String imageUrl,
            String merchantName,
            String offerUrl,
            String price,
            String currencyCode,
            boolean eligible,
            String rejectionReason,
            String recommendationExplanation) {
        public ProductCandidateView(UUID candidateId, String channel3ProductId, String productTitle,
                String brand, String imageUrl, String merchantName, String offerUrl, String price,
                String currencyCode, boolean eligible, String rejectionReason) {
            this(candidateId, channel3ProductId, productTitle, brand, imageUrl, merchantName, offerUrl,
                    price, currencyCode, eligible, rejectionReason, null);
        }
    }
}
