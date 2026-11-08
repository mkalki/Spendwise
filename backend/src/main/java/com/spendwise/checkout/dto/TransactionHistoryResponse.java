package com.spendwise.checkout.dto;

import com.spendwise.payment.entity.PaymentTransaction;
import java.util.List;

public record TransactionHistoryResponse(List<TransactionView> transactions) {

    public record TransactionView(
            String checkoutSessionId,
            String productId,
            String productName,
            String merchantName,
            String retailerOfferUrl,
            String amount,
            String currency,
            String status,
            String paypalOrderId,
            String createdAt,
            String completedAt) {

        public static TransactionView from(PaymentTransaction transaction) {
            return new TransactionView(
                    transaction.getId().toString(),
                    transaction.getProductId(),
                    transaction.getProductName(),
                    transaction.getMerchantName(),
                    transaction.getRetailerOfferUrl(),
                    transaction.getAmount().toPlainString(),
                    transaction.getCurrencyCode(),
                    switch (transaction.getStatus()) {
                        case "CREATED" -> "RESERVED";
                        case "APPROVED" -> "APPROVAL_PENDING";
                        case "COMPLETED" -> "CAPTURED";
                        default -> transaction.getStatus();
                    },
                    transaction.getPaypalOrderId(),
                    transaction.getCreatedAt() == null ? null : transaction.getCreatedAt().toString(),
                    transaction.getCompletedAt() == null ? null : transaction.getCompletedAt().toString());
        }
    }
}
