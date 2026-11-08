package com.spendwise.product.dto;

import com.spendwise.product.entity.ShoppingSearchSession;
import java.time.Instant;
import java.util.List;

public record ShoppingSearchHistoryResponse(List<SearchView> searches, int page, int size, long totalElements) {
    public record SearchView(String searchSessionId, String query, String normalizedQuery,
            String effectiveMaxPrice, Instant createdAt) {
        public static SearchView from(ShoppingSearchSession session) {
            return new SearchView(session.getId().toString(), session.getOriginalQuery(),
                    session.getNormalizedQuery(), session.getEffectiveMaxPrice().toPlainString(),
                    session.getCreatedAt());
        }
    }
}
