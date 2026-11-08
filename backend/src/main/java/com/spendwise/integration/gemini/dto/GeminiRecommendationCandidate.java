package com.spendwise.integration.gemini.dto;

import java.util.UUID;

public record GeminiRecommendationCandidate(UUID candidateId, String productTitle, String brand) {
}
