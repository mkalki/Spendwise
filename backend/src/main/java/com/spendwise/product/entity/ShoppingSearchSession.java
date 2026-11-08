package com.spendwise.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shopping_search_sessions")
public class ShoppingSearchSession {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    @Column(name = "original_query", nullable = false, length = 1000)
    private String originalQuery;

    @Column(name = "normalized_query", nullable = false, length = 1000)
    private String normalizedQuery;

    @Column(name = "effective_max_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal effectiveMaxPrice;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ShoppingSearchSession() {
    }

    public ShoppingSearchSession(
            UUID id,
            String ownerId,
            String originalQuery,
            String normalizedQuery,
            BigDecimal effectiveMaxPrice) {
        this.id = id;
        this.ownerId = ownerId;
        this.originalQuery = originalQuery;
        this.normalizedQuery = normalizedQuery;
        this.effectiveMaxPrice = effectiveMaxPrice;
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getOriginalQuery() { return originalQuery; }
    public String getNormalizedQuery() { return normalizedQuery; }
    public BigDecimal getEffectiveMaxPrice() { return effectiveMaxPrice; }
    public Instant getCreatedAt() { return createdAt; }
}
