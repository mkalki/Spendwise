package com.spendwise.product.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ShoppingRecommendationsResponse(
        List<UUID> rankedCandidateIds,
        Map<UUID, String> explanations,
        String summary,
        boolean aiRanked) {
}
