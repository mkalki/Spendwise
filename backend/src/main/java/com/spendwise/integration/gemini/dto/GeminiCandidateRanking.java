package com.spendwise.integration.gemini.dto;

import java.util.List;
import java.util.UUID;

public record GeminiCandidateRanking(List<UUID> rankedCandidateIds) {
}
