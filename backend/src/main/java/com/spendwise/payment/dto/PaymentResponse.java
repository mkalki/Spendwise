package com.spendwise.payment.dto;

import com.spendwise.payment.entity.PaymentTransaction;

public record PaymentResponse(
        String checkoutSessionId, String orderId, String status, String amount,
        String currency, String approvalUrl, String captureId) {

    public static PaymentResponse from(PaymentTransaction payment) {
        return new PaymentResponse(
                payment.getId().toString(), payment.getPaypalOrderId(), apiStatus(payment.getStatus()),
                payment.getAmount().toPlainString(), payment.getCurrencyCode(),
                payment.getPaypalApprovalUrl(), payment.getPaypalCaptureId());
    }

    private static String apiStatus(String status) {
        return switch (status) {
            case "CREATED" -> "RESERVED";
            case "APPROVED" -> "APPROVAL_PENDING";
            case "COMPLETED" -> "CAPTURED";
            default -> status;
        };
    }
}
