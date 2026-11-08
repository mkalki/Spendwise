package com.spendwise.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_candidates")
public class ProductCandidate {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_session_id", nullable = false)
    private ShoppingSearchSession searchSession;

    @Column(name = "channel3_product_id", nullable = false, length = 200)
    private String channel3ProductId;

    @Column(name = "product_title", nullable = false, length = 500)
    private String productTitle;

    @Column(name = "brand", length = 200)
    private String brand;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "merchant_name", length = 200)
    private String merchantName;

    @Column(name = "offer_url")
    private String offerUrl;

    @Column(name = "listed_price", precision = 12, scale = 2)
    private BigDecimal listedPrice;

    @Column(name = "currency", length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "retrieved_at", nullable = false, insertable = false, updatable = false)
    private Instant retrievedAt;

    protected ProductCandidate() {
    }

    public ProductCandidate(
            UUID id,
            ShoppingSearchSession searchSession,
            String channel3ProductId,
            String productTitle,
            String brand,
            String imageUrl,
            String merchantName,
            String offerUrl,
            BigDecimal listedPrice,
            String currencyCode,
            boolean eligible,
            String rejectionReason) {
        this.id = id;
        this.searchSession = searchSession;
        this.channel3ProductId = channel3ProductId;
        this.productTitle = productTitle;
        this.brand = brand;
        this.imageUrl = imageUrl;
        this.merchantName = merchantName;
        this.offerUrl = offerUrl;
        this.listedPrice = listedPrice;
        this.currencyCode = currencyCode;
        this.eligible = eligible;
        this.rejectionReason = rejectionReason;
    }

    public UUID getId() { return id; }
    public ShoppingSearchSession getSearchSession() { return searchSession; }
    public String getChannel3ProductId() { return channel3ProductId; }
    public String getProductTitle() { return productTitle; }
    public String getBrand() { return brand; }
    public String getImageUrl() { return imageUrl; }
    public String getMerchantName() { return merchantName; }
    public String getOfferUrl() { return offerUrl; }
    public BigDecimal getListedPrice() { return listedPrice; }
    public String getCurrencyCode() { return currencyCode; }
    public boolean isEligible() { return eligible; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getRetrievedAt() { return retrievedAt; }
}
