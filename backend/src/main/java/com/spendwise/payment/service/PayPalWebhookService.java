package com.spendwise.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.exception.ApiException;
import com.spendwise.checkout.service.CheckoutService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PayPalWebhookService {
    private final CheckoutService checkoutService;

    public PayPalWebhookService(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    public void receive(JsonNode event) {
        String eventType = event == null ? null : event.path("event_type").asText(null);
        JsonNode resource = event == null ? null : event.get("resource");
        if (eventType == null || resource == null || !resource.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_WEBHOOK", "The PayPal event payload is invalid.");
        }
        String orderId = switch (eventType) {
            case "CHECKOUT.ORDER.APPROVED", "CHECKOUT.ORDER.VOIDED" -> resource.path("id").asText(null);
            case "PAYMENT.CAPTURE.COMPLETED", "PAYMENT.CAPTURE.DENIED", "PAYMENT.CAPTURE.PENDING",
                    "PAYMENT.CAPTURE.REFUNDED", "PAYMENT.CAPTURE.REVERSED" -> resource.path("supplementary_data")
                    .path("related_ids").path("order_id").asText(null);
            default -> null;
        };
        if (orderId != null && !orderId.isBlank()) {
            checkoutService.reconcilePaypalOrder(orderId);
        }
    }
}
