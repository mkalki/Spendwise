package com.spendwise.checkout.dto;

import com.spendwise.payment.entity.PaymentTransaction;

public record CheckoutResponse(
        String checkoutSessionId,
        String status,
        String amount,
        String currency,
        String approvalUrl,
        PolicyResult policyResult) {

    public static CheckoutResponse from(PaymentTransaction transaction) {
        return new CheckoutResponse(
                transaction.getId().toString(),
                apiStatus(transaction.getStatus()),
                transaction.getAmount().toPlainString(),
                transaction.getCurrencyCode(),
                transaction.getPaypalApprovalUrl(),
                new PolicyResult(true, "Checkout is allowed by the active spending policy."));
    }

    private static String apiStatus(String status) {
        return switch (status) {
            case "CREATED" -> "RESERVED";
            case "APPROVED" -> "APPROVAL_PENDING";
            case "COMPLETED" -> "CAPTURED";
            default -> status;
        };
    }

    public record PolicyResult(boolean eligible, String message) {
    }
}
