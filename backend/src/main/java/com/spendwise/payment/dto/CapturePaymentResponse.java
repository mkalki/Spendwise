package com.spendwise.payment.dto;

import com.spendwise.payment.entity.PaymentTransaction;

public record CapturePaymentResponse(
        String checkoutSessionId, String orderId, String status,
        String amount, String currency, String captureId) {

    public static CapturePaymentResponse from(PaymentTransaction payment) {
        return new CapturePaymentResponse(
                payment.getId().toString(), payment.getPaypalOrderId(),
                "COMPLETED".equals(payment.getStatus()) ? "CAPTURED" : payment.getStatus(),
                payment.getAmount().toPlainString(), payment.getCurrencyCode(), payment.getPaypalCaptureId());
    }
}
