package com.spendwise.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paypal_payment_methods")
public class PayPalPaymentMethod {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    @Column(name = "setup_token_id", length = 100, unique = true)
    private String setupTokenId;

    @Column(name = "vault_token_encrypted", columnDefinition = "text")
    private String vaultTokenEncrypted;

    @Column(name = "paypal_customer_id", length = 100)
    private String paypalCustomerId;

    @Column(name = "paypal_email", length = 320)
    private String paypalEmail;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant consentedAt;

    @Column(name = "consent_version", nullable = false, length = 32, updatable = false)
    private String consentVersion;

    protected PayPalPaymentMethod() { }

    public PayPalPaymentMethod(UUID id, String ownerId, String setupTokenId, String paypalCustomerId) {
        this.id = id;
        this.ownerId = ownerId;
        this.setupTokenId = setupTokenId;
        this.paypalCustomerId = paypalCustomerId;
        this.status = "SETUP_PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.consentedAt = this.createdAt;
        this.consentVersion = "saved-paypal-v1";
    }

    public void markSaved(String encryptedVaultToken, String customerId, String email) {
        this.vaultTokenEncrypted = encryptedVaultToken;
        this.setupTokenId = null;
        if (customerId != null) this.paypalCustomerId = customerId;
        this.paypalEmail = email;
        this.status = "SAVED";
        this.updatedAt = Instant.now();
    }

    public void markRemoved() {
        this.vaultTokenEncrypted = null;
        this.status = "REMOVED";
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getSetupTokenId() { return setupTokenId; }
    public String getVaultTokenEncrypted() { return vaultTokenEncrypted; }
    public String getPaypalCustomerId() { return paypalCustomerId; }
    public String getPaypalEmail() { return paypalEmail; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
