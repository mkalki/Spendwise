package com.spendwise.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final ObjectMapper objectMapper;

    public PayPalWebhookController(PayPalWebhookVerifier verifier, PayPalWebhookService webhookService,
            ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "Receive and verify a PayPal webhook event")
    public ResponseEntity<Void> receive(
            @RequestHeader("PayPal-Auth-Algo") String authAlgo,
            @RequestHeader("PayPal-Cert-Url") String certUrl,
            @RequestHeader("PayPal-Transmission-Id") String transmissionId,
            @RequestHeader("PayPal-Transmission-Sig") String transmissionSignature,
            @RequestHeader("PayPal-Transmission-Time") String transmissionTime,
            @RequestBody String rawEventJson) {
        JsonNode event;
        try {
            event = objectMapper.readTree(rawEventJson);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_WEBHOOK",
                    "The PayPal event payload is invalid.");
        }
        if (event == null || !event.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_WEBHOOK",
                    "The PayPal event payload is invalid.");
        }
        if (!verifier.verify(authAlgo, certUrl, transmissionId, transmissionSignature, transmissionTime, rawEventJson)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "PAYPAL_WEBHOOK_INVALID",
                    "PayPal webhook signature verification failed.");
        }
        webhookService.receive(event);
        return ResponseEntity.noContent().build();
    }
}
