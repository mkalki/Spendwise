package com.spendwise.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    @Column(name = "product_id", nullable = false, length = 200)
    private String productId;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "merchant_name", length = 200)
    private String merchantName;

    @Column(name = "retailer_offer_url")
    private String retailerOfferUrl;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "paypal_order_id", unique = true, length = 100)
    private String paypalOrderId;

    @Column(name = "paypal_approval_url")
    private String paypalApprovalUrl;

    @Column(name = "paypal_capture_id", unique = true, length = 100)
    private String paypalCaptureId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    protected PaymentTransaction() {
    }

    public PaymentTransaction(
            String ownerId,
            String productId,
            String productName,
            String merchantName,
            String retailerOfferUrl,
            BigDecimal amount,
            String currencyCode,
            String idempotencyKey,
            UUID candidateId,
            Instant reservationExpiresAt) {
        this.ownerId = ownerId;
        this.productId = productId;
        this.productName = productName;
        this.merchantName = merchantName;
        this.retailerOfferUrl = retailerOfferUrl;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.status = "CREATED";
        this.idempotencyKey = idempotencyKey;
        this.candidateId = candidateId;
        this.reservationExpiresAt = reservationExpiresAt;
    }

    public void paypalOrderCreated(String paypalOrderId, String paypalApprovalUrl) {
        this.paypalOrderId = paypalOrderId;
        this.paypalApprovalUrl = paypalApprovalUrl;
        this.status = "APPROVED";
    }

    public void markCompleted(String captureId, Instant completedAt) {
        this.paypalCaptureId = captureId;
        this.status = "COMPLETED";
        this.completedAt = completedAt;
        this.reservationExpiresAt = null;
    }

    public void markCapturing() {
        this.status = "CAPTURING";
    }

    public void markApproved() {
        this.status = "APPROVED";
    }

    public void markCancelled() {
        this.status = "CANCELLED";
        this.reservationExpiresAt = null;
    }

    public void markFailed() {
        this.status = "FAILED";
        this.reservationExpiresAt = null;
    }

    public void markExpired() {
        this.status = "EXPIRED";
        this.reservationExpiresAt = null;
    }

    public void markRequiresReconciliation() {
        this.status = "REQUIRES_RECONCILIATION";
    }

    public Long getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getMerchantName() { return merchantName; }
    public String getRetailerOfferUrl() { return retailerOfferUrl; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPaypalOrderId() { return paypalOrderId; }
    public String getPaypalApprovalUrl() { return paypalApprovalUrl; }
    public String getPaypalCaptureId() { return paypalCaptureId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCandidateId() { return candidateId; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
}
