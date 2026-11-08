package com.spendwise.product.dto;

import java.util.List;
import java.util.UUID;

public record ShoppingSearchResponse(
        UUID searchSessionId,
        ShoppingIntent intent,
        List<ProductCandidateView> eligibleCandidates,
        List<ProductCandidateView> rejectedCandidates,
        String recommendation) {

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
            String rejectionReason) {
    }
}
