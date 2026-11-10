package com.spendwise.payment.integration;

import java.math.BigDecimal;

public interface PaymentGateway {

    OrderCreation createOrder(Long transactionId, BigDecimal amount, String currencyCode);

    default VaultOrderCreation createOrderWithVault(
            Long transactionId, BigDecimal amount, String currencyCode, String vaultId, String clientMetadataId) {
        throw new PaymentProviderNotConfiguredException();
    }

    CaptureResult captureOrder(Long transactionId, String paypalOrderId);

    boolean canCancelOrder(String paypalOrderId);

    OrderStatus getOrderStatus(String paypalOrderId);

    CaptureStatus getCaptureStatus(String paypalCaptureId);

    record OrderCreation(String orderId, String approvalUrl) {
    }

    record VaultOrderCreation(String orderId, String approvalUrl, CaptureResult capture) { }

    record CaptureResult(
            boolean completed,
            String captureId,
            BigDecimal amount,
            String currencyCode) {
    }

    record OrderStatus(
            String status, String referenceId, String captureId, BigDecimal amount, String currencyCode) {
    }

    record CaptureStatus(
            String status, String captureId, String orderId, BigDecimal amount, String currencyCode) {
    }
}
