package com.spendwise.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.exception.ApiException;
import com.spendwise.payment.integration.PayPalWebhookVerifier;
import com.spendwise.payment.service.PayPalWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/paypal")
public class PayPalWebhookController {
    private final PayPalWebhookVerifier verifier;
    private final PayPalWebhookService webhookService;

    public PayPalWebhookController(PayPalWebhookVerifier verifier, PayPalWebhookService webhookService) {
        this.verifier = verifier;
        this.webhookService = webhookService;
    }

    @PostMapping
    @Operation(summary = "Receive and verify a PayPal webhook event")
    public ResponseEntity<Void> receive(
            @RequestHeader("PayPal-Auth-Algo") String authAlgo,
            @RequestHeader("PayPal-Cert-Url") String certUrl,
            @RequestHeader("PayPal-Transmission-Id") String transmissionId,
            @RequestHeader("PayPal-Transmission-Sig") String transmissionSignature,
            @RequestHeader("PayPal-Transmission-Time") String transmissionTime,
            @RequestBody JsonNode event) {
        if (!verifier.verify(authAlgo, certUrl, transmissionId, transmissionSignature, transmissionTime, event)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "PAYPAL_WEBHOOK_INVALID",
                    "PayPal webhook signature verification failed.");
        }
        webhookService.receive(event);
        return ResponseEntity.noContent().build();
    }
}
