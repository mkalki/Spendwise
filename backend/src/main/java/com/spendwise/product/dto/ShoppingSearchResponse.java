package com.spendwise.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ShoppingSearchResponse(
        UUID searchSessionId,
        ShoppingIntent intent,
        List<ProductCandidateView> eligibleCandidates,
        List<ProductCandidateView> rejectedCandidates,
        String recommendation,
        String comparisonSummary,
        ShoppingSortBy sortBy,
        boolean aiRanked) {

    public ShoppingSearchResponse(UUID searchSessionId, ShoppingIntent intent,
            List<ProductCandidateView> eligibleCandidates, List<ProductCandidateView> rejectedCandidates,
            String recommendation, String comparisonSummary, ShoppingSortBy sortBy) {
        this(searchSessionId, intent, eligibleCandidates, rejectedCandidates, recommendation, comparisonSummary, sortBy, false);
    }

    public record ProductRating(BigDecimal value, long count, int sourceCount) {
    }

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
            String recommendationExplanation,
            String description,
            ProductRating rating) {
        public ProductCandidateView(UUID candidateId, String channel3ProductId, String productTitle,
                String brand, String imageUrl, String merchantName, String offerUrl, String price,
                String currencyCode, boolean eligible, String rejectionReason) {
            this(candidateId, channel3ProductId, productTitle, brand, imageUrl, merchantName, offerUrl,
                    price, currencyCode, eligible, rejectionReason, null, null, null);
        }
    }
}
