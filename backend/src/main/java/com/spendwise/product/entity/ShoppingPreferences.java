package com.spendwise.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "shopping_preferences")
public class ShoppingPreferences {
    @Id
    @Column(name = "owner_id", length = 120)
    private String ownerId;

    @Column(name = "preferred_categories", nullable = false, columnDefinition = "text")
    private String preferredCategories;

    @Column(name = "preferred_brands", nullable = false, columnDefinition = "text")
    private String preferredBrands;

    @Column(name = "preferred_retailers", nullable = false, columnDefinition = "text")
    private String preferredRetailers;

    @Column(nullable = false, columnDefinition = "text")
    private String keywords;

    @Column(name = "max_price", precision = 12, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShoppingPreferences() {}

    public ShoppingPreferences(String ownerId, String preferredCategories, String preferredBrands,
            String preferredRetailers, String keywords, BigDecimal maxPrice, String currencyCode) {
        this.ownerId = ownerId;
        this.preferredCategories = preferredCategories;
        this.preferredBrands = preferredBrands;
        this.preferredRetailers = preferredRetailers;
        this.keywords = keywords;
        this.maxPrice = maxPrice;
        this.currencyCode = currencyCode;
    }

    public void update(String preferredCategories, String preferredBrands, String preferredRetailers,
            String keywords, BigDecimal maxPrice, String currencyCode) {
        this.preferredCategories = preferredCategories;
        this.preferredBrands = preferredBrands;
        this.preferredRetailers = preferredRetailers;
        this.keywords = keywords;
        this.maxPrice = maxPrice;
        this.currencyCode = currencyCode;
    }

    public String getOwnerId() { return ownerId; }
    public String getPreferredCategories() { return preferredCategories; }
    public String getPreferredBrands() { return preferredBrands; }
    public String getPreferredRetailers() { return preferredRetailers; }
    public String getKeywords() { return keywords; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getUpdatedAt() { return updatedAt; }
}
