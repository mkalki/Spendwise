package com.spendwise.integration.gemini.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GeminiRecommendationResult(
        List<UUID> rankedCandidateIds,
        Map<UUID, String> explanations,
        String summary) {
}
