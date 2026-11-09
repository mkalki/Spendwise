package com.spendwise.payment.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.spendwise.common.config.TransientHttpRetry;
import com.spendwise.common.exception.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PayPalWebhookVerifier {
    private final RestClient restClient;
    private final PayPalAuthService authService;
    private final String webhookId;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayPalWebhookVerifier(ClientHttpRequestFactory requestFactory, PayPalAuthService authService,
            ObjectMapper objectMapper,
            @Value("${spendwise.paypal.base-url}") String baseUrl,
            @Value("${spendwise.paypal.webhook-id:}") String webhookId) {
        this(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build(), authService, webhookId,
                objectMapper);
    }

    PayPalWebhookVerifier(RestClient restClient, PayPalAuthService authService, String webhookId) {
        this(restClient, authService, webhookId, new ObjectMapper());
    }

    PayPalWebhookVerifier(RestClient restClient, PayPalAuthService authService, String webhookId,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.authService = authService;
        this.webhookId = webhookId;
        this.objectMapper = objectMapper;
    }

    public boolean verify(String authAlgo, String certUrl, String transmissionId,
            String transmissionSignature, String transmissionTime, String rawEventJson) {
        if (!StringUtils.hasText(webhookId)) throw new PaymentProviderNotConfiguredException();
        if (!StringUtils.hasText(authAlgo) || !StringUtils.hasText(certUrl)
                || !StringUtils.hasText(transmissionId) || !StringUtils.hasText(transmissionSignature)
                || !StringUtils.hasText(transmissionTime) || !StringUtils.hasText(rawEventJson)) {
            return false;
        }
        try {
            JsonNode event = objectMapper.readTree(rawEventJson);
            if (event == null || !event.isObject()) return false;
        } catch (JsonProcessingException exception) {
            return false;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auth_algo", authAlgo);
        payload.put("cert_url", certUrl);
        payload.put("transmission_id", transmissionId);
        payload.put("transmission_sig", transmissionSignature);
        payload.put("transmission_time", transmissionTime);
        payload.put("webhook_id", webhookId);
        String requestBody;
        try {
            String headerJson = objectMapper.writeValueAsString(payload);
            requestBody = headerJson.substring(0, headerJson.length() - 1)
                    + ",\"webhook_event\":" + rawEventJson + "}";
        } catch (JsonProcessingException exception) {
            throw new PaymentProviderException();
        }
        try {
            JsonNode response = TransientHttpRetry.execute(() -> restClient.post()
                    .uri("/v1/notifications/verify-webhook-signature")
                    .headers(headers -> {
                        headers.setBearerAuth(authService.accessToken());
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class), false);
            return response != null && "SUCCESS".equals(response.path("verification_status").asText());
        } catch (ResourceAccessException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYPAL_WEBHOOK_VERIFICATION_TIMEOUT",
                    "PayPal could not verify the webhook in time.");
        } catch (PaymentProviderNotConfiguredException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "PayPal webhook verification is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYPAL_WEBHOOK_VERIFICATION_TIMEOUT",
                    "PayPal could not verify the webhook in time.");
        } catch (PaymentProviderRejectedException | PaymentProviderException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYPAL_WEBHOOK_VERIFICATION_FAILED",
                    "PayPal could not verify the webhook.");
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYPAL_WEBHOOK_VERIFICATION_FAILED",
                    "PayPal could not verify the webhook.");
        }
    }
}
