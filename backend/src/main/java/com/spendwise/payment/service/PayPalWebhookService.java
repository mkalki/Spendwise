package com.spendwise.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.audit.service.AuditService;
import com.spendwise.common.exception.ApiException;
import com.spendwise.checkout.service.CheckoutService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PayPalWebhookService {
    private final CheckoutService checkoutService;
    private final AuditService auditService;

    public PayPalWebhookService(CheckoutService checkoutService, AuditService auditService) {
        this.checkoutService = checkoutService;
        this.auditService = auditService;
    }

    public void receive(JsonNode event) {
        if (event == null || !event.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_WEBHOOK", "The PayPal event payload is invalid.");
        }
        String eventType = event.path("event_type").asText(null);
        if (eventType == null || eventType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_WEBHOOK", "The PayPal event payload is invalid.");
        }
        String eventId = event.path("id").asText(null);
        auditService.record(null, "PAYPAL_WEBHOOK_RECEIVED", eventId,
                java.util.Map.of("eventType", eventType));
        if (!isSupported(eventType)) return;

        JsonNode resource = event.get("resource");
        if (resource == null || !resource.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_WEBHOOK", "The PayPal event payload is invalid.");
        }
        if ("PAYMENT.CAPTURE.REFUNDED".equals(eventType) || "PAYMENT.CAPTURE.REVERSED".equals(eventType)) {
            String captureId = resource.path("id").asText(null);
            if (captureId != null && !captureId.isBlank()) {
                checkoutService.reconcilePaypalCapture(captureId, "PAYMENT.CAPTURE.REVERSED".equals(eventType));
            }
            return;
        }
        String orderId = switch (eventType) {
            case "CHECKOUT.ORDER.APPROVED", "CHECKOUT.ORDER.COMPLETED", "CHECKOUT.ORDER.VOIDED" ->
                    resource.path("id").asText(null);
            case "PAYMENT.CAPTURE.COMPLETED", "PAYMENT.CAPTURE.DENIED", "PAYMENT.CAPTURE.PENDING" ->
                    resource.path("supplementary_data")
                    .path("related_ids").path("order_id").asText(null);
            default -> null;
        };
        if (orderId != null && !orderId.isBlank()) {
            checkoutService.reconcilePaypalOrder(orderId);
        }
    }

    private boolean isSupported(String eventType) {
        return switch (eventType) {
            case "CHECKOUT.ORDER.APPROVED", "CHECKOUT.ORDER.COMPLETED", "CHECKOUT.ORDER.VOIDED",
                    "PAYMENT.CAPTURE.COMPLETED", "PAYMENT.CAPTURE.DENIED", "PAYMENT.CAPTURE.PENDING",
                    "PAYMENT.CAPTURE.REFUNDED", "PAYMENT.CAPTURE.REVERSED" -> true;
            default -> false;
        };
    }
}
