package com.spendwise.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendwise.payment.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentTransactionStateTest {

    @Test
    void persistsVerifiedProviderSuccessAndReleasesReservation() {
        PaymentTransaction payment = payment();
        Instant completedAt = Instant.parse("2026-11-08T10:00:00Z");
        payment.syncProviderStatus("COMPLETED", "capture-123", completedAt);

        assertThat(payment.getStatus()).isEqualTo("COMPLETED");
        assertThat(payment.getPaypalCaptureId()).isEqualTo("capture-123");
        assertThat(payment.getCompletedAt()).isEqualTo(completedAt);
        assertThat(payment.getReservationExpiresAt()).isNull();
    }

    @Test
    void persistsProviderVoidAndReleasesReservation() {
        PaymentTransaction payment = payment();
        payment.syncProviderStatus("VOIDED", null, Instant.now());

        assertThat(payment.getStatus()).isEqualTo("CANCELLED");
        assertThat(payment.getReservationExpiresAt()).isNull();
    }

    @Test
    void leavesPaymentAndReservationUnchangedForUnrecognizedProviderStatus() {
        PaymentTransaction payment = payment();
        Instant expiry = payment.getReservationExpiresAt();
        payment.syncProviderStatus("DENIED", null, Instant.now());

        assertThat(payment.getStatus()).isEqualTo("APPROVED");
        assertThat(payment.getPaypalCaptureId()).isNull();
        assertThat(payment.getReservationExpiresAt()).isEqualTo(expiry);
    }

    private PaymentTransaction payment() {
        PaymentTransaction payment = new PaymentTransaction(
                "owner", "product", "Product", "merchant", "https://merchant.test/product",
                new BigDecimal("20.00"), "USD", "idempotency-key", null,
                Instant.parse("2026-11-08T11:00:00Z"));
        payment.paypalOrderCreated("order-123", "https://www.sandbox.paypal.com/checkoutnow?token=order-123");
        return payment;
    }
}
