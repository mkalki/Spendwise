package com.spendwise.payment.integration;

import java.math.BigDecimal;

public interface PaymentGateway {

    OrderCreation createOrder(Long transactionId, BigDecimal amount, String currencyCode);

    CaptureResult captureOrder(Long transactionId, String paypalOrderId);

    boolean canCancelOrder(String paypalOrderId);

    OrderStatus getOrderStatus(String paypalOrderId);

    record OrderCreation(String orderId, String approvalUrl) {
    }

    record CaptureResult(
            boolean completed,
            String captureId,
            BigDecimal amount,
            String currencyCode) {
    }

    record OrderStatus(
            String status, String referenceId, String captureId, BigDecimal amount, String currencyCode) {
    }
}
