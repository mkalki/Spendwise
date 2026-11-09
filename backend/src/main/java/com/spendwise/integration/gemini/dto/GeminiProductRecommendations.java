package com.spendwise.integration.gemini.dto;

import java.util.List;
import java.util.UUID;

public record GeminiProductRecommendations(String summary, List<CandidateExplanation> recommendations) {
    public record CandidateExplanation(UUID candidateId, String explanation) { }
}
