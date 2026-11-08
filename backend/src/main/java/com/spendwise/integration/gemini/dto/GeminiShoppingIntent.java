package com.spendwise.integration.gemini.dto;

import java.math.BigDecimal;
import java.util.List;

public record GeminiShoppingIntent(
        String normalizedQuery,
        String category,
        BigDecimal budget,
        String budgetCurrency,
        String useCase,
        List<String> preferences) {
}
