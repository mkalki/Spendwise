package com.spendwise.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "spending_policies")
public class SpendingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, unique = true, length = 120)
    private String ownerId;

    @Column(name = "max_per_product", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxProductPrice;

    @Column(name = "max_per_order", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxOrderAmount;

    @Column(name = "weekly_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal weeklyBudget;

    @Column(name = "max_orders_per_week", nullable = false)
    private int maxOrdersPerWeek;

    @Column(name = "currency", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "country", nullable = false, length = 2)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String countryCode;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "hard_limits_enabled", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SpendingPolicy() {
    }

    public SpendingPolicy(
            String ownerId,
            BigDecimal maxProductPrice,
            BigDecimal maxOrderAmount,
            BigDecimal weeklyBudget,
            int maxOrdersPerWeek,
            String currencyCode,
            String countryCode,
            String timezone) {
        this.ownerId = ownerId;
        this.maxProductPrice = maxProductPrice;
        this.maxOrderAmount = maxOrderAmount;
        this.weeklyBudget = weeklyBudget;
        this.maxOrdersPerWeek = maxOrdersPerWeek;
        this.currencyCode = currencyCode;
        this.countryCode = countryCode;
        this.timezone = timezone;
    }

    public void update(
            BigDecimal maxProductPrice,
            BigDecimal maxOrderAmount,
            BigDecimal weeklyBudget,
            int maxOrdersPerWeek,
            String currencyCode,
            String countryCode,
            String timezone) {
        this.maxProductPrice = maxProductPrice;
        this.maxOrderAmount = maxOrderAmount;
        this.weeklyBudget = weeklyBudget;
        this.maxOrdersPerWeek = maxOrdersPerWeek;
        this.currencyCode = currencyCode;
        this.countryCode = countryCode;
        this.timezone = timezone;
    }

    @PrePersist
    void setCreationTimestamps() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public BigDecimal getMaxProductPrice() { return maxProductPrice; }
    public BigDecimal getMaxOrderAmount() { return maxOrderAmount; }
    public BigDecimal getWeeklyBudget() { return weeklyBudget; }
    public int getMaxOrdersPerWeek() { return maxOrdersPerWeek; }
    public String getCurrencyCode() { return currencyCode; }
    public String getCountryCode() { return countryCode; }
    public String getTimezone() { return timezone; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
